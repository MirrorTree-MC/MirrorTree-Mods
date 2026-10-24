"""Targeted package/update checks using disposable Fabric JAR fixtures."""

import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from hashlib import sha256
from io import StringIO
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile

import package_distribution
import update_mods


def jar(path, identifier, version="1", content="original"):
    with ZipFile(path, "w") as archive:
        archive.writestr(
            "fabric.mod.json",
            json.dumps({"id": identifier, "version": version, "environment": "*"}),
        )
        archive.writestr("payload", content)
    return path


class UpdateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.package = self.root / "package"
        (self.package / "mods").mkdir(parents=True)
        self.target = self.root / "instance/mods"
        self.target.mkdir(parents=True)
        self.source = jar(self.package / "mods/new.jar", "managed", "2", "updated")
        self.manifest = {
            "schema_version": 1,
            "side": "server",
            "mods": [
                {
                    "id": "managed",
                    "version": "2",
                    "file": "mods/new.jar",
                    "sha256": sha256(self.source.read_bytes()).hexdigest(),
                }
            ],
        }
        (self.package / "manifest.json").write_text(
            json.dumps(self.manifest), encoding="utf-8"
        )
        self.old = jar(self.target / "arbitrary-old-name.jar", "managed")
        self.old_data = self.old.read_bytes()
        self.unrelated = jar(self.target / "unrelated.jar", "unrelated")
        self.unrelated_data = self.unrelated.read_bytes()

    def update(self, apply=False, side="server"):
        with redirect_stdout(StringIO()):
            return update_mods.update(self.package, self.target, side, apply)

    def test_preview_changes_nothing(self):
        before = sorted(p.relative_to(self.root) for p in self.root.rglob("*"))
        self.update()
        self.assertEqual(
            before, sorted(p.relative_to(self.root) for p in self.root.rglob("*"))
        )
        self.assertEqual(self.old_data, self.old.read_bytes())

    def test_replace_by_id_backup_duplicates_and_preserve_unrelated(self):
        duplicate = jar(self.target / "another-copy.jar", "managed")
        backup = self.update(True)
        self.assertEqual(self.old_data, (backup / self.old.name).read_bytes())
        self.assertTrue((backup / duplicate.name).is_file())
        self.assertFalse(self.old.exists())
        self.assertFalse(duplicate.exists())
        self.assertEqual(
            self.source.read_bytes(), (self.target / "new.jar").read_bytes()
        )
        self.assertEqual(self.unrelated_data, self.unrelated.read_bytes())
        self.assertIsNone(self.update(True))

    def test_tampered_package_and_wrong_side_do_not_change_install(self):
        with self.assertRaises(ValueError):
            self.update(True, "client")
        self.source.write_bytes(b"corrupt")
        with self.assertRaises(ValueError):
            self.update(True)
        self.assertEqual(self.old_data, self.old.read_bytes())
        self.assertFalse((self.target.parent / "mod-update-backups").exists())

    def test_source_changed_during_staging_is_rejected_before_installed_moves(self):
        copy = update_mods.shutil.copy2

        def tamper_during_copy(source, destination):
            source.write_bytes(b"changed after manifest verification")
            return copy(source, destination)

        with (
            patch.object(update_mods.shutil, "copy2", tamper_during_copy),
            self.assertRaises(OSError),
        ):
            self.update(True)
        self.assertEqual(self.old_data, self.old.read_bytes())
        self.assertEqual(self.unrelated_data, self.unrelated.read_bytes())
        self.assertFalse((self.target / "new.jar").exists())

    def test_unrelated_filename_collision_is_rejected(self):
        collision = jar(self.target / "new.jar", "unrelated2")
        original = collision.read_bytes()
        with self.assertRaises(ValueError):
            self.update(True)
        self.assertEqual(original, collision.read_bytes())
        self.assertEqual(self.old_data, self.old.read_bytes())

    def test_failed_install_restores_old_jars(self):
        with (
            patch.object(
                update_mods.os, "replace", side_effect=OSError("simulated disk error")
            ),
            self.assertRaises(OSError),
        ):
            self.update(True)
        self.assertEqual(self.old_data, self.old.read_bytes())
        self.assertEqual(self.unrelated_data, self.unrelated.read_bytes())
        self.assertFalse((self.target / "new.jar").exists())

    def test_failure_after_first_install_restores_both_mods(self):
        second = jar(self.package / "mods/second.jar", "managed2", "2")
        second_old = jar(self.target / "second-old.jar", "managed2")
        second_data = second_old.read_bytes()
        self.manifest["mods"].append(
            {
                "id": "managed2",
                "version": "2",
                "file": "mods/second.jar",
                "sha256": sha256(second.read_bytes()).hexdigest(),
            }
        )
        (self.package / "manifest.json").write_text(
            json.dumps(self.manifest), encoding="utf-8"
        )
        replace = update_mods.os.replace

        def fail_second(source, destination):
            if destination.name == "second.jar":
                raise OSError("simulated second copy failure")
            return replace(source, destination)

        with (
            patch.object(update_mods.os, "replace", side_effect=fail_second),
            self.assertRaises(OSError),
        ):
            self.update(True)
        self.assertEqual(self.old_data, self.old.read_bytes())
        self.assertEqual(second_data, second_old.read_bytes())
        self.assertFalse((self.target / "new.jar").exists())

    def test_backup_record_failure_precedes_installed_file_moves(self):
        with (
            patch.object(
                Path,
                "write_text",
                side_effect=OSError("simulated backup record failure"),
            ),
            self.assertRaises(OSError),
        ):
            self.update(True)
        self.assertEqual(self.old_data, self.old.read_bytes())
        self.assertEqual(self.unrelated_data, self.unrelated.read_bytes())

    def test_one_restore_failure_does_not_skip_other_mods(self):
        second = jar(self.package / "mods/second.jar", "managed2", "2")
        jar(self.target / "second-old.jar", "managed2")
        self.manifest["mods"].append(
            {
                "id": "managed2",
                "version": "2",
                "file": "mods/second.jar",
                "sha256": sha256(second.read_bytes()).hexdigest(),
            }
        )
        (self.package / "manifest.json").write_text(
            json.dumps(self.manifest), encoding="utf-8"
        )
        rename = Path.rename

        def fail_one_restore(source, destination):
            if source.parent != self.target and source.name == "second-old.jar":
                raise OSError("simulated locked destination")
            return rename(source, destination)

        with (
            patch.object(
                update_mods.os,
                "replace",
                side_effect=OSError("simulated install failure"),
            ),
            patch.object(Path, "rename", fail_one_restore),
            self.assertRaisesRegex(RuntimeError, "Backup:"),
        ):
            self.update(True)
        self.assertEqual(self.old_data, self.old.read_bytes())
        self.assertEqual(self.unrelated_data, self.unrelated.read_bytes())
        self.assertEqual(
            1,
            len(
                list(
                    (self.target.parent / "mod-update-backups").rglob("second-old.jar")
                )
            ),
        )


class PackageTests(unittest.TestCase):
    def test_cli_failure_log_stays_under_build(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "tools").mkdir()
            script = root / "tools/package_distribution.py"
            script.write_bytes(Path(package_distribution.__file__).read_bytes())
            result = subprocess.run(
                [
                    sys.executable,
                    str(script),
                    "--side",
                    "server",
                    "--artifacts",
                    "missing.json",
                ],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(1, result.returncode)
            self.assertIn("FAIL package-server", result.stderr)
            self.assertIn("Log:", result.stderr)
            self.assertNotIn("Traceback", result.stderr)
            self.assertIn(
                "FileNotFoundError",
                (root / "build/logs/package-server.log").read_text(encoding="utf-8"),
            )
            self.assertEqual([], list(root.glob("*.log")))

    def test_test_mod_rejected_and_zip_is_reproducible(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in (
                "example/validation/Probe.class",
                "cn/sh1rocu/touhoulittlemaid/test/OrihimeClientTest.class",
            ):
                path = jar(root / "test.jar", "probe")
                with ZipFile(path, "a") as archive:
                    archive.writestr(name, b"probe")
                with self.assertRaises(ValueError):
                    package_distribution.inspect_jar(path.read_bytes())
            first, second = root / "first.zip", root / "second.zip"
            package_distribution.write_zip(first, {"a": b"a", "b": b"b"})
            package_distribution.write_zip(second, {"b": b"b", "a": b"a"})
            self.assertEqual(first.read_bytes(), second.read_bytes())


if __name__ == "__main__":
    unittest.main()
