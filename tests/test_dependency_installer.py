import argparse
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "installer", Path(__file__).resolve().parents[1] / "tools/install-dependency.py")
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class InstallerTests(unittest.TestCase):
    def test_private_source_falls_back_only_for_access_errors(self):
        entry = {"repository": "owner/assets", "asset_path": "tlibs.jar", "ref": "pinned",
                 "fallback": {"repository": "owner/legacy", "tag": "v1", "filename": "TLibs.jar"}}
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "tlibs.jar"
            with patch.dict(installer.os.environ, {"GH_TOKEN": "test-token"}), \
                    patch.object(installer.urllib.request, "urlopen", side_effect=urllib.error.HTTPError(
                        "https://api.github.com/test", 404, "Not found", None, None)), \
                    patch.object(installer.subprocess, "run") as run:
                installer.download(entry, destination)
                self.assertIn("owner/legacy", run.call_args.args[0])
            with patch.dict(installer.os.environ, {"GH_TOKEN": "test-token"}), \
                    patch.object(installer.urllib.request, "urlopen", side_effect=urllib.error.HTTPError(
                        "https://api.github.com/test", 500, "Server error", None, None)), \
                    patch.object(installer.subprocess, "run") as run:
                with self.assertRaises(urllib.error.HTTPError):
                    installer.download(entry, destination)
                run.assert_not_called()

    def test_reads_only_the_tlibs_dependency_version(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / "pom.xml"
            pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
              <version>9.0</version><properties><tlibs.version>1.0-pinned</tlibs.version></properties>
              <dependencies><dependency><groupId>me.plugins</groupId><artifactId>tlibs</artifactId>
              <version>${tlibs.version}</version></dependency></dependencies></project>''')
            self.assertEqual("1.0-pinned", installer.version_from_pom(pom))

    def test_rejects_a_pom_without_tlibs(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / "pom.xml"
            pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>')
            with self.assertRaisesRegex(ValueError, "No me.plugins:tlibs"):
                installer.version_from_pom(pom)

    def test_corrupt_input_never_reaches_maven(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "input.jar"
            jar.write_bytes(b"wrong binary")
            args = argparse.Namespace(jar=jar, assets=None, mvn="mvn", maven_repo=None)
            with patch.object(installer.subprocess, "run") as run:
                with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                    installer.install("1.0-test", {"sha256": "0" * 64}, args)
                run.assert_not_called()

    def test_install_uses_clean_pom_and_keeps_input_outside_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "input.jar"
            jar.write_bytes(b"verified fixture")
            args = argparse.Namespace(jar=jar, assets=None, mvn="mvn", maven_repo=Path(directory) / "cache")
            def check(command, **kwargs):
                work = Path(kwargs["cwd"])
                self.assertNotEqual(jar.parent, work)
                self.assertIn("-Dmaven.repo.local=" + str(args.maven_repo), command)
                pom = (work / "pom.xml").read_text()
                self.assertIn("<version>1.0-test</version>", pom)
                self.assertNotIn("systemPath", pom)
                self.assertNotIn("<dependencies>", pom)
                self.assertEqual(jar.read_bytes(), (work / "tlibs.jar").read_bytes())
            with patch.object(installer.subprocess, "run", side_effect=check) as run:
                installer.install("1.0-test", {"sha256": hashlib.sha256(jar.read_bytes()).hexdigest()}, args)
                run.assert_called_once()
            self.assertEqual(["input.jar"], [p.name for p in jar.parent.iterdir()])


if __name__ == "__main__":
    unittest.main()
