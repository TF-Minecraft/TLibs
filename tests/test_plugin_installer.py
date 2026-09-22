import argparse
import importlib.util
import json
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('plugins', Path(__file__).resolve().parents[1] / 'tools/install-plugins.py')
plugins = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plugins)


class PluginInstallerTests(unittest.TestCase):
    def fixture(self, path):
        text = '''<project xmlns="http://maven.apache.org/POM/4.0.0"><version>9.0</version>
        <properties><a.version>1.0</a.version><b.version>2.0</b.version></properties>
        <dependencies>
        <dependency><groupId>tfmc</groupId><artifactId>a</artifactId><version>${a.version}</version><scope>provided</scope></dependency>
        <dependency><groupId>tfmc</groupId><artifactId>b</artifactId><version>${b.version}</version><scope>provided</scope></dependency>
        </dependencies></project>'''
        path.write_text(text)
        catalog = {f'tfmc:{name}': {'property': name + '.version', 'repository': 'TF-Minecraft/' + name,
                                  'filename': name + '-{version}.jar'} for name in ('a', 'b')}
        args = argparse.Namespace(mvn='mvn', maven_repo=None, assets=None)
        return text, catalog, args

    def test_latest_resolves_only_direct_inputs_with_real_coordinates(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / 'pom.xml'
            original, catalog, args = self.fixture(pom)
            with patch.object(plugins.installer, 'release_artifact', side_effect=[
                    ('1.1', {'sha256': 'a' * 64}), ('2.1', {'sha256': 'b' * 64})]) as release, \
                    patch.object(plugins.installer, 'install') as install:
                resolved = plugins.prepare(pom, 'latest', args, catalog)
            self.assertEqual(2, release.call_count)  # No recursive expansion of cyclic provider POMs.
            self.assertEqual([('tfmc', 'a'), ('tfmc', 'b')], [c.args[3:] for c in install.call_args_list])
            self.assertEqual(['tfmc:a:1.1', 'tfmc:b:2.1'], [r['coordinates'] for r in resolved])
            self.assertEqual(original.replace('>1.0<', '>1.1<').replace('>2.0<', '>2.1<'), pom.read_text())

    def test_failed_second_input_leaves_pom_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / 'pom.xml'
            original, catalog, args = self.fixture(pom)
            with patch.object(plugins.installer, 'release_artifact', return_value=('3.0', {'sha256': 'a' * 64})), \
                    patch.object(plugins.installer, 'install', side_effect=[None, ValueError('checksum mismatch')]):
                with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                    plugins.prepare(pom, 'latest', args, catalog)
            self.assertEqual(original, pom.read_text())

    def test_pinned_uses_exact_tags_and_preserves_pom(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / 'pom.xml'
            original, catalog, args = self.fixture(pom)
            with patch.object(plugins.installer, 'release_artifact', side_effect=[
                    ('1.0', {'sha256': 'a' * 64}), ('2.0', {'sha256': 'b' * 64})]) as release, \
                    patch.object(plugins.installer, 'install'):
                plugins.prepare(pom, 'pinned', args, catalog)
            self.assertEqual(['1.0', '2.0'], [c.args[2] for c in release.call_args_list])
            self.assertEqual(original, pom.read_text())

    def test_private_input_never_resolves_a_public_release_and_restores_token(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / 'pom.xml'
            _, catalog, args = self.fixture(pom)
            catalog = {'tfmc:a': {'property': 'a.version', 'versions': {
                '1.0': {'sha256': 'a' * 64, 'repository': 'TF-Minecraft/ServerAssets'}}}}
            def check(*arguments):
                self.assertEqual('private-test', plugins.os.environ['GH_TOKEN'])
            with patch.dict(plugins.os.environ, {'GH_TOKEN': 'public-test', 'TFMC_PRIVATE_TOKEN': 'private-test'}), \
                    patch.object(plugins.installer, 'release_artifact') as release, \
                    patch.object(plugins.installer, 'install', side_effect=check):
                resolved = plugins.prepare(pom, 'latest', args, catalog)
                self.assertEqual('public-test', plugins.os.environ['GH_TOKEN'])
                self.assertEqual('private-pinned', resolved[0]['selection'])
                release.assert_not_called()

    def test_public_provider_uses_legacy_private_bytes_only_when_pinned(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / 'pom.xml'
            _, catalog, args = self.fixture(pom)
            catalog = {'tfmc:a': dict(catalog['tfmc:a'], versions={
                '1.0': {'sha256': 'a' * 64, 'repository': 'TF-Minecraft/ServerAssets'}})}
            with patch.dict(plugins.os.environ, {'TFMC_PRIVATE_TOKEN': 'private-test'}), \
                    patch.object(plugins.installer, 'release_artifact',
                                 return_value=('1.2', {'sha256': 'b' * 64})) as release, \
                    patch.object(plugins.installer, 'install'):
                old = plugins.prepare(pom, 'pinned', args, catalog)
                release.assert_not_called()
                current = plugins.prepare(pom, 'latest', args, catalog)
                self.assertEqual(1, release.call_count)
            self.assertEqual('tfmc:a:1.0', old[0]['coordinates'])
            self.assertEqual('private-pinned', old[0]['selection'])
            self.assertEqual('tfmc:a:1.2', current[0]['coordinates'])
            self.assertEqual('latest', current[0]['selection'])

    def test_preview_channel_skips_drafts_and_keeps_exact_asset(self):
        release = {'tag_name': 'v0.1.5-ALPHA', 'prerelease': True, 'draft': False,
                   'assets': [{'name': 'cooking-0.1.5-ALPHA.jar',
                               'digest': 'sha256:' + 'a' * 64,
                               'browser_download_url': 'https://example.test/cooking.jar'}]}
        payload = [dict(release, draft=True, tag_name='v0.1.6-ALPHA'), release]
        with patch.object(plugins.installer.urllib.request, 'urlopen',
                          return_value=io.BytesIO(json.dumps(payload).encode())) as request:
            version, entry = plugins.installer.release_artifact(
                'TF-Minecraft/Cooking', 'cooking-{version}.jar', allow_prerelease=True)
        self.assertEqual('0.1.5-ALPHA', version)
        self.assertEqual('a' * 64, entry['sha256'])
        self.assertIn('releases?per_page=100', request.call_args.args[0].full_url)

    def test_managed_dependencies_must_be_provided(self):
        with tempfile.TemporaryDirectory() as directory:
            pom = Path(directory) / 'pom.xml'
            original, catalog, _ = self.fixture(pom)
            pom.write_text(original.replace('<scope>provided</scope>', '<scope>compile</scope>'))
            with self.assertRaisesRegex(ValueError, 'provided scope'):
                plugins.dependencies(pom, catalog)


if __name__ == '__main__':
    unittest.main()
