"""Release filename compatibility across the lowercase artifact migration."""
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('naming_installer', Path(__file__).resolve().parents[1] / 'tools/install-dependency.py')
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class ArtifactNamingTests(unittest.TestCase):
    def release(self, names, digest=True):
        return {'tag_name': 'v1.2.3', 'draft': False, 'prerelease': False,
                'assets': [{'name': name, 'digest': 'sha256:' + 'a' * 64 if digest else None,
                            'browser_download_url': 'https://example.test/' + name} for name in names]}

    def resolve(self, payload, *responses):
        with patch.object(installer.urllib.request, 'urlopen', side_effect=[
                io.BytesIO(json.dumps(payload).encode()), *[io.BytesIO(r.encode()) for r in responses]]):
            return installer.release_artifact('TF-Minecraft/TLibs', 'tlibs-{version}.jar',
                                              legacy_filename_patterns=['TLibs-{version}.jar'])

    def test_canonical_and_explicit_legacy_names(self):
        for filename in ('tlibs-1.2.3.jar', 'TLibs-1.2.3.jar'):
            with self.subTest(filename=filename):
                version, entry = self.resolve(self.release([filename]))
                self.assertEqual('1.2.3', version)
                self.assertTrue(entry['url'].endswith('/' + filename))
                self.assertEqual('a' * 64, entry['sha256'])

    def test_prefers_canonical_when_both_names_exist(self):
        _, entry = self.resolve(self.release(['TLibs-1.2.3.jar', 'tlibs-1.2.3.jar']))
        self.assertTrue(entry['url'].endswith('/tlibs-1.2.3.jar'))

    def test_never_selects_an_unlisted_jar(self):
        for name in ('unrelated-1.2.3.jar', 'TLIBS-1.2.3.jar', 'tlibs-1.2.3-sources.jar'):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'has no'):
                self.resolve(self.release([name]))

    def test_checksum_uses_selected_filename(self):
        for name in ('tlibs-1.2.3.jar', 'TLibs-1.2.3.jar'):
            payload = self.release([name, 'SHA256SUMS'], digest=False)
            _, entry = self.resolve(payload, 'b' * 64 + '  ' + name + '\n')
            self.assertEqual('b' * 64, entry['sha256'])
            with self.assertRaisesRegex(ValueError, 'checksum'):
                self.resolve(payload, 'b' * 64 + '  wrong.jar\n')

    def test_standalone_installer_accepts_lowercase_release(self):
        payload = self.release(['tlibs-1.2.3.jar'])
        with patch.object(installer.urllib.request, 'urlopen',
                          return_value=io.BytesIO(json.dumps(payload).encode())):
            _, entry = installer.latest_release()
        self.assertTrue(entry['url'].endswith('/tlibs-1.2.3.jar'))


if __name__ == '__main__':
    unittest.main()
