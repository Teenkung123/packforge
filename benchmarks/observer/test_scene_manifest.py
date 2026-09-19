import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

MODULE = importlib.util.spec_from_file_location('scene_manifest', Path(__file__).with_name('scene_manifest.py'))
scene = importlib.util.module_from_spec(MODULE)
MODULE.loader.exec_module(scene)


class SceneManifestTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.observer = self.root / 'observer.jar'
        with zipfile.ZipFile(self.observer, 'w') as archive:
            for name in scene.CLASSES:
                archive.writestr(name, b'fixture-' + name.encode())
        for name in scene.SOURCES:
            source = self.root / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text('fixture ' + name)

    def tearDown(self):
        self.directory.cleanup()

    def test_runtime_class_digest_contract(self):
        digest = hashlib.sha256()
        for name in scene.CLASSES:
            digest.update(('/' + name).encode())
            digest.update(b'\0')
            digest.update(b'fixture-' + name.encode())
        self.assertEqual(scene.implementation_hash(self.observer), digest.hexdigest())

    def test_source_change_does_not_claim_same_source(self):
        first = scene.manifest(self.observer, self.root)
        (self.root / scene.SOURCES[0]).write_text('changed source')
        second = scene.manifest(self.observer, self.root)
        self.assertNotEqual(first['sourceSha256'], second['sourceSha256'])
        self.assertEqual(first['implementationSha256'], second['implementationSha256'])
        self.assertEqual(first['observerSha256'], second['observerSha256'])

    def test_missing_scene_class_rejected(self):
        with zipfile.ZipFile(self.observer, 'w') as archive:
            archive.writestr(scene.CLASSES[0], b'fixture')
        with self.assertRaises(ValueError):
            scene.manifest(self.observer, self.root)

    def test_manifest_has_no_save_input_and_distinct_entity_identity(self):
        result = scene.manifest(self.observer, self.root)
        self.assertEqual(result['mode'], 'observer-created')
        self.assertEqual(len(set(result['entityUuids'])), 3)
        self.assertEqual(result['seed'], scene.SEED)
        self.assertNotIn('worldPath', result)
        self.assertNotIn('save', result)


if __name__ == '__main__':
    unittest.main()
