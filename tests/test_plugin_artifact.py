import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / ".github/scripts/plugin-artifact.py"


class PluginArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "target").mkdir()

    def jar(self, version="1.2.3", descriptor="plugin.yml", text=None, name=None):
        path = self.root / "target" / (name or f"plugin-{version}.jar")
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr(descriptor, text if text is not None else f"name: Plugin\nversion: {version}\n")
        return path

    def run_check(self, path, version="1.2.3", *extra):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--jar", str(path), "--version", version, *extra],
            cwd=self.root, env=os.environ | {
                "GITHUB_REPOSITORY": "TF-Minecraft/Test", "GITHUB_SHA": "abc123",
                "TAG": "v1.2.3", "GITHUB_SERVER_URL": "https://github.com", "GITHUB_RUN_ID": "42",
            }, capture_output=True, text=True,
        )

    def test_release_and_development_versions(self):
        for version in ("1.2.3", "2.1", "0.2.1-ALPHA", "DEV-20260922-2300"):
            with self.subTest(version=version):
                self.assertEqual(self.run_check(self.jar(version), version).returncode, 0)

    def test_quoted_paper_descriptor(self):
        path = self.jar(descriptor="paper-plugin.yml", text='version: "1.2.3" # release\r\n')
        self.assertEqual(self.run_check(path).returncode, 0)

    def test_rejects_mismatched_or_unfiltered_descriptor(self):
        for text in ("version: 1.0\n", "version: ${project.version}\n", "version: 1.2.3\nversion: 1.2.3\n"):
            with self.subTest(text=text):
                result = self.run_check(self.jar(text=text))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("version must match", result.stderr)

    def test_rejects_wrong_filename_and_classifiers(self):
        for name in ("plugin-1.0.jar", "plugin-1.2.3-sources.jar", "plugin-1.2.3-tests.jar"):
            self.assertNotEqual(self.run_check(self.jar(name=name)).returncode, 0)

    def test_rejects_missing_descriptor_and_corrupt_archive(self):
        path = self.jar(descriptor="README.txt")
        self.assertNotEqual(self.run_check(path).returncode, 0)
        path.write_text("not a zip")
        self.assertNotEqual(self.run_check(path).returncode, 0)

    def test_rejects_symlink_outside_target(self):
        path = self.jar()
        outside = self.root / path.name
        path.rename(outside)
        path.symlink_to(outside)
        self.assertNotEqual(self.run_check(path).returncode, 0)

    def test_stages_only_runtime_jar_with_matching_metadata(self):
        path = self.jar()
        self.jar(name="original-plugin-1.2.3.jar")
        lock = self.root / "dependencies.json"
        dependencies = [{"coordinates": "test:dependency:1.0", "sha256": "abcd"}]
        lock.write_text(json.dumps(dependencies))
        dest = self.root / "release"
        result = self.run_check(path, "1.2.3", "--stage", str(dest), "--dependencies", str(lock))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual({p.name for p in dest.iterdir()}, {path.name, "SHA256SUMS", "build.json"})
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        self.assertEqual((dest / "SHA256SUMS").read_text(), f"{digest}  {path.name}\n")
        metadata = json.loads((dest / "build.json").read_text())
        self.assertEqual(metadata["sha256"], digest)
        self.assertEqual(metadata["plugin_dependencies"], dependencies)

    def test_missing_dependency_metadata_fails_before_staging(self):
        dest = self.root / "release"
        result = self.run_check(self.jar(), "1.2.3", "--stage", str(dest), "--dependencies", "missing.json")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(dest.exists())

    def test_stages_plugins_without_shared_dependencies(self):
        dest = self.root / "release"
        result = self.run_check(self.jar(), "1.2.3", "--stage", str(dest))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads((dest / "build.json").read_text())["plugin_dependencies"], [])

    def test_rejects_release_tag_that_disagrees_with_version(self):
        dest = self.root / "release"
        result = self.run_check(self.jar("1.2.4"), "1.2.4", "--stage", str(dest))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Release tag must match", result.stderr)
        self.assertFalse(dest.exists())


if __name__ == "__main__":
    unittest.main()
