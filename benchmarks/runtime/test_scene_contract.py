import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

SPEC=importlib.util.spec_from_file_location('runner',Path(__file__).with_name('runner.py'))
runner=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(runner)
scene=runner.scene_contract


def fixture(root):
    observer=root/'observer.jar'
    with zipfile.ZipFile(observer,'x') as archive:
        for name in scene.CLASSES:archive.writestr(name,('test class bytes: '+name).encode())
    sources=[{'path':name,'sha256':scene.digest(name.encode())} for name in scene.SOURCES]
    value={'schema':1,'generatedBy':'packbench','mode':'observer-created','worldId':scene.WORLD_ID,
           'seed':scene.SEED,'entityUuids':scene.ENTITY_UUIDS[:],'observerSha256':runner.digest(observer),
           'implementationSha256':scene.implementation_hash(observer),
           'sourceSha256':scene.digest(json.dumps(sources,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()),
           'sources':sources}
    manifest=root/'manifest.json';manifest.write_text(json.dumps(value),encoding='utf-8')
    contract={key:value[key] for key in scene.MIRRORED}
    contract.update(manifestPath=str(manifest),manifestSha256=runner.digest(manifest))
    return contract,{'id':'observer','path':str(observer),'sha256':runner.digest(observer)},value


class SceneContractTests(unittest.TestCase):
    def test_exact_compiled_source_manifest_pins_generate_properties_without_world_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp).resolve();contract,observer,_=fixture(root)
            self.assertEqual(contract,scene.validate(contract,observer))
            args=scene.arguments(contract)
            self.assertIn('-Dpackforge.benchmark.expectedSceneSeed=5782977387033873987',args)
            self.assertIn('-Dpackforge.benchmark.expectedSceneImplementationSha256='+contract['implementationSha256'],args)
            self.assertEqual(4,len(args))
            self.assertFalse((root/'saves').exists())
            spec={'benchmarkSuite':'full-modpack','scenario':'inworld','framebuffer':{'width':2736,'height':1824},
                  'pack':{'id':'DevelopRP-Full-1.9.5.zip'},'generatedScene':contract}
            self.assertTrue(set(args)<=set(runner.observer_integrity_arguments(spec)))

    def test_seed_uuid_and_existing_save_inputs_cannot_change_contract(self):
        with tempfile.TemporaryDirectory() as temp:
            contract,observer,_=fixture(Path(temp).resolve())
            for mutation in (lambda c:c.update(seed=scene.SEED+1),lambda c:c.update(seed=float(scene.SEED)),
                             lambda c:c.update(entityUuids=list(reversed(scene.ENTITY_UUIDS))),
                             lambda c:c.update(worldId='original-world'),lambda c:c.update(files=[{'path':'original.dat'}]),
                             lambda c:c.update(observerSha256='a'*64),lambda c:c.update(manifestPath='relative.json')):
                invalid=copy.deepcopy(contract);mutation(invalid)
                with self.assertRaises(ValueError):scene.validate(invalid,observer)

    def test_manifest_tampering_and_mirrored_source_changes_rejected(self):
        for mutation in (lambda v:v.update(schema=True),lambda v:v.update(seed=scene.SEED+1),
                         lambda v:v['sources'][0].update(sha256='0'*64),
                         lambda v:v['sources'].reverse()):
            with tempfile.TemporaryDirectory() as temp:
                contract,observer,value=fixture(Path(temp).resolve())
                mutation(value)
                manifest=Path(contract['manifestPath']);manifest.write_text(json.dumps(value))
                with self.assertRaises(ValueError):scene.validate(contract,observer)
                # Re-hashing a modified manifest alone still cannot hide an
                # unexpected schema, source inventory, seed or mirrored value.
                contract['manifestSha256']=runner.digest(manifest)
                with self.assertRaises(ValueError):scene.validate(contract,observer)

    def test_changed_class_rejected_even_if_observer_and_manifest_rehashed(self):
        with tempfile.TemporaryDirectory() as temp:
            contract,observer,value=fixture(Path(temp).resolve())
            path=Path(observer['path'])
            with zipfile.ZipFile(path,'w') as archive:
                for name in scene.CLASSES:archive.writestr(name,('different '+name).encode())
            changed=runner.digest(path)
            observer['sha256']=contract['observerSha256']=value['observerSha256']=changed
            manifest=Path(contract['manifestPath']);manifest.write_text(json.dumps(value));contract['manifestSha256']=runner.digest(manifest)
            with self.assertRaisesRegex(ValueError,'implementation checksum'):scene.validate(contract,observer)

    def test_duplicate_or_missing_compiled_scene_classes_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp).resolve();contract,observer,_=fixture(root)
            with warnings.catch_warnings():
                warnings.simplefilter('ignore',UserWarning)
                with zipfile.ZipFile(observer['path'],'a') as archive:archive.writestr(scene.CLASSES[0],b'duplicate')
            with self.assertRaisesRegex(ValueError,'exactly one'):scene.implementation_hash(observer['path'])
            other=root/'missing.jar'
            with zipfile.ZipFile(other,'x') as archive:archive.writestr(scene.CLASSES[0],b'only one')
            with self.assertRaisesRegex(ValueError,'exactly one'):scene.implementation_hash(other)


if __name__=='__main__':unittest.main()
