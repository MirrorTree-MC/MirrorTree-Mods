"""Validate installation ZIP boundaries and write release checksums."""

import json
import re
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/distributions"
MODULES = {
    "listorm": "lantern-in-storm-rebirth",
    "journey": "mirrortree",
    "sync-sign-notice": "sync-sign-notice",
}
SIDES = {"client": tuple(MODULES), "server": ("listorm", "journey")}


def check_jar(data, module, version):
    source = json.loads(
        (ROOT / module / "src/main/resources/fabric.mod.json").read_text(
            encoding="utf-8"
        )
    )
    with ZipFile(BytesIO(data)) as jar:
        names = set(jar.namelist())
        metadata = json.loads(jar.read("fabric.mod.json"))
        assert metadata["id"] == source["id"], module
        assert metadata["version"] == version, module
        assert metadata["environment"] == source["environment"], module
        assert metadata["depends"] == source["depends"], module
        assert metadata["license"] == source["license"], module
        assert "LICENSE" in names, module
        assert not any(
            "/test/" in name or "/validation/" in name or "GameTest" in name
            for name in names
        ), module
        assert not any(
            name.startswith(("config/", "data/storm_test/")) for name in names
        ), module
        assert not any("gametest" in key for key in metadata.get("entrypoints", {})), (
            module
        )
        for entries in metadata.get("entrypoints", {}).values():
            for entry in entries:
                name = entry if isinstance(entry, str) else entry["value"]
                assert name.replace(".", "/") + ".class" in names, (module, name)
        for entry in metadata.get("mixins", []):
            config = entry if isinstance(entry, str) else entry["config"]
            mixin = json.loads(jar.read(config))
            for name in (
                mixin.get("mixins", [])
                + mixin.get("client", [])
                + mixin.get("server", [])
            ):
                assert (mixin["package"] + "." + name).replace(
                    ".", "/"
                ) + ".class" in names, (module, name)
        if module == "listorm":
            assert "mirrorskills" in metadata["provides"]
            for name in ("MirrorSkills", "client/MirrorSkillsClient", "data/SkillData"):
                assert f"top/bearcabbage/mirrorskills/{name}.class" in names, name
            assert "licenses/MirrorSkills-LICENSE" in names
            manifest = json.loads((ROOT / "docs/skill-tree-source.json").read_text())[
                "sha256"
            ]
            for name, digest in manifest.items():
                entry = (
                    "licenses/MirrorTree-Skill-Tree-LICENSE"
                    if name == "LICENSE"
                    else name
                )
                assert sha256(jar.read(entry)).hexdigest() == digest, entry
            for category, count in (("combat", 16), ("support", 42)):
                skills = json.loads(
                    jar.read(
                        f"data/mirrortree/puffish_skills/categories/{category}/skills.json"
                    )
                )
                assert len(skills) == count, category
        if module == "sync-sign-notice":
            nested = [
                entry["file"]
                for entry in metadata.get("jars", [])
                if "mysql-connector-j" in entry["file"]
            ]
            assert len(nested) == 1, "Missing bundled JDBC driver"
            with ZipFile(BytesIO(jar.read(nested[0]))) as driver:
                assert "com/mysql/cj/jdbc/Driver.class" in driver.namelist()


def main():
    minecraft = re.search(
        r"^minecraft_version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M
    )[1]
    versions = {}
    for module in MODULES:
        versions[module] = re.search(
            r"^version = '([^']+)'$", (ROOT / module / "build.gradle").read_text(), re.M
        )[1]
    checksums = []
    for side, modules in SIDES.items():
        archive = OUTPUT / f"MirrorTree-Mods-{minecraft}-{side}.zip"
        expected = {f"mods/{MODULES[m]}-{versions[m]}.jar": m for m in modules}
        with ZipFile(archive) as package:
            names = set(package.namelist())
            assert {n for n in names if n.endswith(".jar")} == set(expected), (
                side,
                names,
            )
            assert package.read("README.txt").strip(), side
            manifest = json.loads(package.read("manifest.json"))
            assert manifest["schema_version"] == 1 and manifest["side"] == side, side
            assert manifest["minecraft"] == minecraft, side
            assert {entry["file"] for entry in manifest["mods"]} == set(expected), side
            for entry in manifest["mods"]:
                assert (
                    sha256(package.read(entry["file"])).hexdigest() == entry["sha256"]
                ), entry["id"]
            assert (
                package.read("update_mods.py")
                == (ROOT / "tools/update_mods.py").read_bytes()
            ), side
            for line in package.read("SHA256SUMS").decode().splitlines():
                checksum, name = line.split("  ", 1)
                assert sha256(package.read(name)).hexdigest() == checksum, name
            assert not any(
                n.startswith(("config/", "world/", "worlds/")) for n in names
            ), side
            for name, module in expected.items():
                assert f"licenses/{module}/LICENSE" in names, module
                data = package.read(name)
                assert (
                    data
                    == (ROOT / module / "build/libs" / Path(name).name).read_bytes()
                ), name
                check_jar(data, module, versions[module])
        checksums.append(f"{sha256(archive.read_bytes()).hexdigest()}  {archive.name}")
        print(
            f"PASS {archive.name}: {len(expected)} production mods, entrypoints, mixins, licenses, dependencies"
        )
    (OUTPUT / "SHA256SUMS").write_text("\n".join(checksums) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
