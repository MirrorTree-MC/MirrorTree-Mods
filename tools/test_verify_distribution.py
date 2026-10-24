"""Release checks must not silently accept malformed metadata under Python -O."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


class VerificationTests(unittest.TestCase):
    def test_wrong_mod_id_is_never_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "example/src/main/resources/fabric.mod.json"
            source.parent.mkdir(parents=True)
            metadata = {
                "id": "expected",
                "version": "1",
                "environment": "*",
                "depends": {},
                "license": "MIT",
            }
            source.write_text(json.dumps(metadata), encoding="utf-8")
            metadata["id"] = "wrong"
            jar = root / "malformed.jar"
            with ZipFile(jar, "w") as archive:
                archive.writestr("fabric.mod.json", json.dumps(metadata))
                archive.writestr("LICENSE", "MIT")
            script = (
                "import sys; from pathlib import Path; import verify_distribution as v; "
                "v.ROOT=Path(sys.argv[1]); "
                "v.check_jar(Path(sys.argv[2]).read_bytes(), 'example', '1')"
            )
            for flags, diagnostic in [([], "AssertionError"), (["-O"], "without -O")]:
                with self.subTest(flags=flags):
                    result = subprocess.run(
                        [sys.executable, *flags, "-c", script, str(root), str(jar)],
                        cwd=Path(__file__).parent,
                        capture_output=True,
                        text=True,
                        check=False,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(diagnostic, result.stderr)


if __name__ == "__main__":
    unittest.main()
