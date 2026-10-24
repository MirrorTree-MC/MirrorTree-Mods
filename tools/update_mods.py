"""Preview or apply an extracted MirrorTree mod package. Python 3.10+."""

import argparse
import json
import os
import shutil
import tempfile
from datetime import datetime
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


def mod_id(path):
    with ZipFile(path) as jar:
        if "fabric.mod.json" not in jar.namelist():
            return None
        return json.loads(jar.read("fabric.mod.json"))["id"]


def update(package, target, side, apply=False):
    manifest = json.loads((package / "manifest.json").read_text(encoding="utf-8"))
    if manifest["schema_version"] != 1 or manifest["side"] != side:
        raise ValueError("Package schema or client/server side does not match")
    if target.is_symlink() or (hasattr(target, "is_junction") and target.is_junction()):
        raise ValueError("The mods directory must not be a link/junction")
    target = target.resolve(strict=True)
    if not target.is_dir():
        raise ValueError("The mods directory must already exist")
    incoming = {}
    for entry in manifest["mods"]:
        name = Path(entry["file"]).name
        if entry["file"] != "mods/" + name or not name.endswith(".jar"):
            raise ValueError("Invalid package JAR path")
        source = package / "mods" / name
        if (
            source.is_symlink()
            or digest(source) != entry["sha256"]
            or mod_id(source) != entry["id"]
        ):
            raise ValueError(f"Package integrity check failed: {name}")
        if entry["id"] in incoming or any(
            item[0].name == name for item in incoming.values()
        ):
            raise ValueError("Duplicate package mod ID or filename")
        incoming[entry["id"]] = (source, entry["sha256"])
    existing = {identifier: [] for identifier in incoming}
    for path in sorted(target.iterdir()):
        if path.suffix.lower() != ".jar":
            continue
        if path.is_symlink() or not path.is_file():
            raise ValueError(f"Refusing linked/non-file JAR: {path.name}")
        identifier = mod_id(path)
        if identifier in existing:
            existing[identifier].append(path)
    changes = []
    for identifier, (source, checksum) in incoming.items():
        old = existing[identifier]
        destination = target / source.name
        if destination.exists() and destination not in old:
            raise ValueError(f"Filename belongs to another mod: {destination.name}")
        if old == [destination] and digest(destination) == checksum:
            print(f"UNCHANGED {identifier}")
            continue
        print(
            f"UPDATE {identifier}: {', '.join(p.name for p in old) or '(new)'} -> {source.name}"
        )
        changes.append((source, old))
    if not apply or not changes:
        print(
            "Preview only; stop the instance, then add --apply."
            if changes
            else "Already up to date."
        )
        return None
    # Stage every new JAR before moving any installed file. Backups stay beside mods.
    backup_root = target.parent / "mod-update-backups"
    if backup_root.is_symlink() or (
        hasattr(backup_root, "is_junction") and backup_root.is_junction()
    ):
        raise ValueError("Backup directory must not be a link/junction")
    backup = backup_root / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    backup.mkdir(parents=True)
    (backup / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    moved, installed = [], []
    with tempfile.TemporaryDirectory(
        prefix=".mod-update-", dir=target.parent
    ) as staging_dir:
        staging = Path(staging_dir)
        for source, _ in changes:
            shutil.copy2(source, staging / source.name)
            if digest(staging / source.name) != digest(source):
                raise OSError(f"Staged copy failed verification: {source.name}")
        try:
            for _, old in changes:
                for path in old:
                    path.rename(backup / path.name)
                    moved.append(path)
            for source, _ in changes:
                destination = target / source.name
                os.replace(staging / source.name, destination)
                installed.append(destination)
        except BaseException as failure:
            recovery_errors = []
            for path in installed:
                try:
                    path.unlink()
                except OSError as error:
                    recovery_errors.append(f"{path.name}: {error}")
            for path in reversed(moved):
                try:
                    (backup / path.name).rename(path)
                except OSError as error:
                    recovery_errors.append(f"{path.name}: {error}")
            if recovery_errors:
                raise RuntimeError(
                    f"Update failed; recovery needs attention. Backup: {backup}\n"
                    + "\n".join(recovery_errors)
                ) from failure
            raise
    print(f"Applied. Old JAR backup: {backup}")
    print("Restart the instance to load these JARs.")
    return backup


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mods",
        type=Path,
        required=True,
        help="Target instance's existing mods directory",
    )
    parser.add_argument("--side", choices=("client", "server"), required=True)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply after stopping the instance; default is preview",
    )
    args = parser.parse_args()
    update(Path(__file__).resolve().parent, args.mods, args.side, args.apply)


if __name__ == "__main__":
    main()
