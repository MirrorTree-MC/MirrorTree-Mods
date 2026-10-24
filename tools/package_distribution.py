"""Package only Gradle-selected production artifacts; no build/libs globbing."""

import argparse
import json
import sys
import traceback
from contextlib import redirect_stdout
from hashlib import sha256
from io import BytesIO, StringIO
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]


def inspect_jar(data):
    with ZipFile(BytesIO(data)) as jar:
        names = set(jar.namelist())
        metadata = json.loads(jar.read("fabric.mod.json"))
        if "${" in metadata["version"]:
            raise ValueError("Unexpanded mod version")
        if any("gametest" in key.lower() for key in metadata.get("entrypoints", {})):
            raise ValueError("GameTest entrypoint in production JAR")
        if any(
            "GameTest" in name
            or "/gametest/" in name
            or "/test/" in name
            or "/validation/" in name
            for name in names
        ):
            raise ValueError("Test/probe classes in production JAR")
        for entries in metadata.get("entrypoints", {}).values():
            for entry in entries:
                name = entry if isinstance(entry, str) else entry["value"]
                if name.split("::")[0].replace(".", "/") + ".class" not in names:
                    raise ValueError(f"Missing entrypoint: {name}")
        bundled = []
        for entry in metadata.get("jars", []):
            with ZipFile(BytesIO(jar.read(entry["file"]))) as nested:
                if "fabric.mod.json" in nested.namelist():
                    nested_meta, nested_bundled = inspect_jar(jar.read(entry["file"]))
                    bundled.append(
                        {"id": nested_meta["id"], "version": nested_meta["version"]}
                    )
                    bundled.extend(nested_bundled)
        return metadata, bundled


def write_zip(path, files):
    with ZipFile(path, "w", ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    with ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Corrupt package: {path}")
        for name, data in files.items():
            if archive.read(name) != data:
                raise ValueError(f"Package differs from selected artifact: {name}")


def package(artifacts, side):
    config = json.loads((ROOT / "tools/distribution.json").read_text(encoding="utf-8"))
    selected = {
        Path(record["projectDirectory"]).resolve(): Path(record["artifact"])
        for record in json.loads(artifacts.read_text(encoding="utf-8"))
    }
    files = {"update_mods.py": (ROOT / "tools/update_mods.py").read_bytes()}
    mods = []
    for module in config["modules"]:
        if side not in module["sides"]:
            continue
        directory = ROOT / module["directory"]
        jar_path = selected[directory.resolve()]
        data = jar_path.read_bytes()
        metadata, bundled = inspect_jar(data)
        with ZipFile(BytesIO(data)) as jar:
            for entry in module.get("required_entries", []):
                if entry not in jar.namelist():
                    raise ValueError(f"Missing release dependency or notice: {entry}")
        if metadata["id"] != module["id"] or metadata.get("environment", "*") not in (
            "*",
            side,
        ):
            raise ValueError(f"Wrong mod ID or side: {jar_path}")
        if jar_path.stem.endswith(("-sources", "-dev", "-test")):
            raise ValueError(f"Not a production artifact: {jar_path}")
        name = "mods/" + jar_path.name
        if name in files:
            raise ValueError(f"Duplicate filename: {name}")
        files[name] = data
        mods.append(
            {
                "id": metadata["id"],
                "version": metadata["version"],
                "file": name,
                "sha256": sha256(data).hexdigest(),
                "environment": metadata.get("environment", "*"),
                "depends": metadata.get("depends", {}),
                "bundled": bundled,
            }
        )
        for notice in module["notices"]:
            path = directory / notice
            for source in sorted(path.rglob("*")) if path.is_dir() else [path]:
                if source.is_file():
                    files[
                        f"licenses/{module['directory']}/{source.relative_to(directory).as_posix()}"
                    ] = source.read_bytes()
                elif not source.exists():
                    raise FileNotFoundError(source)
    manifest = {
        "schema_version": 1,
        "project": config["project"],
        "minecraft": config["minecraft"],
        "side": side,
        "mods": mods,
    }
    files["manifest.json"] = (
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    ).encode()
    listing = "\n".join(f"- {mod['id']} {mod['version']}" for mod in mods)
    readme = f"{config['project']} / Minecraft {config['minecraft']} / {side}\n\n{listing}\n\n"
    readme += (ROOT / f"dist/README-{side}.txt").read_text(encoding="utf-8")
    readme += "\n\n解压后使用 Python 3.10+ 预览（Windows 可将 python3 改为 python）：\n"
    readme += f'python3 update_mods.py --side {side} --mods "/path/to/instance/mods"\n'
    readme += "停止目标实例后，在同一命令后加 --apply 执行更新；旧 JAR 保存在实例的 mod-update-backups/。\n"
    readme += "工具按 Fabric mod ID 替换包内模组，保留其他模组和配置。首次安装也使用同一命令。\n"
    readme += "依赖版本与内嵌依赖见 manifest.json；本包不下载或安装第三方依赖。\n"
    files["README.txt"] = readme.encode()
    files["SHA256SUMS"] = "".join(
        f"{sha256(data).hexdigest()}  {name}\n" for name, data in sorted(files.items())
    ).encode()
    output = ROOT / "build/distributions"
    output.mkdir(parents=True, exist_ok=True)
    archive = output / f"{config['project']}-{config['minecraft']}-{side}.zip"
    write_zip(archive, files)
    archive.with_suffix(".zip.sha256").write_text(
        f"{sha256(archive.read_bytes()).hexdigest()}  {archive.name}\n",
        encoding="utf-8",
    )
    print(
        f"PASS {archive.name}: {len(mods)} production mods, metadata, nested dependencies and checksums"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--side", choices=("client", "server"), required=True)
    args = parser.parse_args()
    log = ROOT / f"build/logs/package-{args.side}.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    captured = StringIO()
    try:
        with redirect_stdout(captured):
            package(args.artifacts, args.side)
    except Exception as error:
        log.write_text(captured.getvalue() + traceback.format_exc(), encoding="utf-8")
        print(f"FAIL package-{args.side}: {error}\nLog: {log}", file=sys.stderr)
        raise SystemExit(1) from None
    log.write_text(captured.getvalue(), encoding="utf-8")
    print(captured.getvalue().rstrip())
    print(f"Log: {log}")
