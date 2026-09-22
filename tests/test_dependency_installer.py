import argparse
import hashlib
import io
import json
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
    def release(self, **changes):
        release = {"tag_name": "v1.2.3", "draft": False, "prerelease": False,
                   "assets": [{"name": "TLibs-1.2.3.jar", "digest": "sha256:" + "a" * 64,
                               "browser_download_url": "https://example.test/v1.2.3/TLibs-1.2.3.jar"}]}
        release.update(changes)
        return release

    def test_latest_resolves_exact_version_and_asset_digest_without_catalog(self):
        with patch.object(installer.urllib.request, "urlopen",
                          return_value=io.BytesIO(json.dumps(self.release()).encode())) as request:
            version, entry = installer.latest_release()
        self.assertEqual("1.2.3", version)
        self.assertEqual("a" * 64, entry["sha256"])
        self.assertIn("/v1.2.3/", entry["url"])
        self.assertTrue(request.call_args.args[0].full_url.endswith("/releases/latest"))

    def test_latest_rejects_unpublished_unversioned_and_missing_artifact(self):
        for changes in ({"draft": True}, {"prerelease": True}, {"tag_name": "latest"},
                        {"tag_name": "v1.2.3-rc1"}, {"assets": []}):
            with self.subTest(changes=changes), patch.object(installer.urllib.request, "urlopen",
                    return_value=io.BytesIO(json.dumps(self.release(**changes)).encode())):
                with self.assertRaises(ValueError):
                    installer.latest_release()

    def test_latest_checksum_fallback_supports_both_release_formats(self):
        for name in ("TLibs-1.2.3.jar.sha256", "SHA256SUMS"):
            release = self.release()
            release["assets"][0].pop("digest")
            release["assets"].append({"name": name, "browser_download_url": "https://example.test/sums"})
            with self.subTest(name=name), patch.object(installer.urllib.request, "urlopen", side_effect=[
                    io.BytesIO(json.dumps(release).encode()),
                    io.BytesIO(("b" * 64 + "  TLibs-1.2.3.jar\n").encode())]):
                self.assertEqual("b" * 64, installer.latest_release()[1]["sha256"])

    def test_latest_requires_an_unambiguous_matching_checksum(self):
        release = self.release()
        release["assets"][0].pop("digest")
        with patch.object(installer.urllib.request, "urlopen",
                          return_value=io.BytesIO(json.dumps(release).encode())):
            with self.assertRaisesRegex(ValueError, "no SHA-256"):
                installer.latest_release()
        release["assets"].append({"name": "SHA256SUMS", "browser_download_url": "https://example.test/sums"})
        for content in ("b" * 64 + "  wrong.jar", ("b" * 64 + "  TLibs-1.2.3.jar\n") * 2):
            with patch.object(installer.urllib.request, "urlopen", side_effect=[
                    io.BytesIO(json.dumps(release).encode()), io.BytesIO(content.encode())]):
                with self.assertRaisesRegex(ValueError, "Invalid TLibs release checksum"):
                    installer.latest_release()

    def test_latest_updates_only_tlibs_after_success_and_outputs_exact_version(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / "pom.xml"
            original = '''<project xmlns="http://maven.apache.org/POM/4.0.0">
              <version>9.0</version><properties><tlibs.version>1.1.0</tlibs.version></properties>
              <dependencies><dependency><groupId>me.plugins</groupId><artifactId>tlibs</artifactId>
              <version>${tlibs.version}</version></dependency></dependencies></project>'''
            pom.write_text(original)
            output = Path(directory) / "outputs"
            with patch.object(installer.sys, "argv", ["installer", "--pom", str(pom), "--latest",
                                                      "--github-output", str(output)]), \
                    patch.object(installer, "latest_release", return_value=("1.2.3", {"sha256": "a" * 64})), \
                    patch.object(installer, "install", side_effect=ValueError("checksum mismatch")):
                self.assertEqual(1, installer.main())
                self.assertEqual(original, pom.read_text())
                self.assertFalse(output.exists())
            with patch.object(installer.sys, "argv", ["installer", "--pom", str(pom), "--latest",
                                                      "--github-output", str(output)]), \
                    patch.object(installer, "latest_release", return_value=("1.2.3", {"sha256": "a" * 64})), \
                    patch.object(installer, "install") as install:
                self.assertEqual(0, installer.main())
                self.assertEqual("1.2.3", install.call_args.args[0])
                self.assertEqual(original.replace("1.1.0", "1.2.3"), pom.read_text())
                self.assertIn("version=1.2.3\n", output.read_text())

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
