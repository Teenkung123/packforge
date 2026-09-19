import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC=importlib.util.spec_from_file_location('runner',Path(__file__).with_name('runner.py'))
runner=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(runner)
normalization=runner.normalization


def fixture(root):
    contents={
        'config/iris.properties':b'#Iris settings\n#Thu Sep 10 11:24:57 GMT+07:00 2026\nenableShaders=true\n',
        'shaderpacks/ComplementaryUnbound_r5.7.1.zip.txt':b'#Thu Sep 10 11:24:58 GMT+07:00 2026\nSHADOW_QUALITY=4\n',
        'config/sodium-fingerprint.json':json.dumps({'v':1,'s':'a'*128,'u':'b'*128,'p':'c'*128,'t':1789014296}).encode(),
        'config/sodium-options.json':json.dumps({'quality':{'pixel_filtering_mode':'NEAREST'},'performance':{'animate_only_visible_textures':True},
                                               'notifications':{'has_seen_donation_prompt':True,'has_edited_fullscreen_option':True}}).encode(),
    }
    paths=[]
    for relative,data in contents.items():
        path=root/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);paths.append(path)
    return paths


class NormalizationTests(unittest.TestCase):
    def test_only_java_properties_generated_header_date_is_ignored(self):
        relative='config/iris.properties'
        before=b'#Iris\n#Thu Sep 10 11:24:57 GMT+07:00 2026\nenableShaders=true\n'
        after=before.replace(b'11:24:57',b'11:30:59')
        self.assertEqual(normalization.canonical(relative,before),normalization.canonical(relative,after))
        for changed in (after.replace(b'true',b'false'),after.replace(b'#Iris',b'#Different comment')):
            self.assertNotEqual(normalization.canonical(relative,before),normalization.canonical(relative,changed))
        with self.assertRaises(ValueError):normalization.canonical(relative,b'key=continued\\\n#Thu Sep 10 11:24:57 GMT+07:00 2026\n')
        with self.assertRaises(ValueError):normalization.canonical('config/unknown.properties',before)

    def test_fingerprint_schema_exact_and_notification_other_settings_preserved(self):
        fingerprint={'v':1,'s':'a'*128,'u':'b'*128,'p':'c'*128,'t':1}
        original=normalization.canonical('config/sodium-fingerprint.json',json.dumps(fingerprint).encode())
        changed={**fingerprint,'p':'d'*128,'t':2}
        self.assertEqual(original,normalization.canonical('config/sodium-fingerprint.json',json.dumps(changed).encode()))
        for changed in ({**fingerprint,'v':2},{**fingerprint,'quality':'fast'},{**fingerprint,'t':True},{**fingerprint,'p':'invalid'}):
            with self.assertRaises(ValueError):normalization.canonical('config/sodium-fingerprint.json',json.dumps(changed).encode())
        with self.assertRaises(ValueError):normalization.canonical('config/sodium-options.json',b'{"notifications":{"has_seen_donation_prompt":false,"has_seen_donation_prompt":true}}')

    def test_audit_preserves_raw_changes_and_allows_only_observed_notification_reset(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp).resolve();paths=fixture(root)
            before=normalization.capture(root,paths,normalization.POLICY)
            iris=root/'config/iris.properties';iris.write_bytes(iris.read_bytes().replace(b'11:24:57',b'12:24:57'))
            fingerprint=root/'config/sodium-fingerprint.json';value=json.loads(fingerprint.read_text());value['p']='d'*128;fingerprint.write_text(json.dumps(value))
            options=root/'config/sodium-options.json';value=json.loads(options.read_text());value['notifications']['has_seen_donation_prompt']=False;options.write_text(json.dumps(value))
            records,accepted=normalization.audit(before)
            self.assertEqual(3,len(accepted))
            self.assertTrue(all(r['accepted'] for r in records))
            self.assertTrue(all(r['rawDiff'] for r in records if r['rawChanged']))
            self.assertTrue(all(r['beforeSha256']!=r['afterSha256'] for r in records if r['rawChanged']))
            options_value=json.loads(options.read_text());options_value['performance']['animate_only_visible_textures']=False;options.write_text(json.dumps(options_value))
            records,accepted=normalization.audit(before)
            self.assertFalse(next(r for r in records if r['id']=='config/sodium-options.json')['accepted'])

    def test_notification_flip_without_fingerprint_change_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp).resolve();paths=fixture(root);before=normalization.capture(root,paths,normalization.POLICY)
            options=root/'config/sodium-options.json';value=json.loads(options.read_text());value['notifications']['has_seen_donation_prompt']=False;options.write_text(json.dumps(value))
            records,_=normalization.audit(before)
            self.assertFalse(next(r for r in records if r['id']=='config/sodium-options.json')['accepted'])

    def test_postflight_keeps_raw_hashes_and_rejects_shader_change(self):
        for meaningful in (False,True):
            with self.subTest(meaningful=meaningful),tempfile.TemporaryDirectory() as temp:
                root=Path(temp).resolve();controlled=fixture(root)
                options=root/'options.txt';options.write_text('mipmapLevels:2\n')
                pinned=controlled+[options]
                before=normalization.capture(root,controlled,normalization.POLICY)
                manifest={'inputHashes':runner.snapshot(pinned),'exitCode':0,'timeout':False,'sessionId':'session','context':{}}
                original=copy.deepcopy(manifest['inputHashes'])
                iris=root/'config/iris.properties';iris.write_bytes(iris.read_bytes().replace(b'11:24:57',b'12:24:57'))
                if meaningful:iris.write_bytes(iris.read_bytes().replace(b'enableShaders=true',b'enableShaders=false'))
                (root/'events.jsonl').write_text('{"event":"complete","sessionId":"session"}\n')
                runner.postflight(manifest,root,pinned,controlled,{'mipmapLevels':'2'},before)
                self.assertEqual(original,manifest['inputHashes'])
                self.assertIn('inputHashesAfter',manifest)
                self.assertFalse(manifest['rawInputsUnchanged'])
                self.assertEqual(not meaningful,manifest['valid'])
                self.assertEqual(not meaningful,manifest['inputsUnchanged'])
                self.assertEqual(4,len(manifest['normalizationAudit']))

    def test_unreviewed_changed_file_not_covered_by_other_accepted_normalization(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp).resolve();controlled=fixture(root)
            other=root/'config/packforge.json';other.write_text('{"fontPrepareProviderSelectionEnabled":true}')
            controlled.append(other)
            options=root/'options.txt';options.write_text('mipmapLevels:2\n');pinned=controlled+[options]
            before=normalization.capture(root,controlled,normalization.POLICY)
            manifest={'inputHashes':runner.snapshot(pinned),'exitCode':0,'timeout':False,'sessionId':'session','context':{}}
            other.write_text('{"fontPrepareProviderSelectionEnabled":false}')
            (root/'events.jsonl').write_text('{"event":"complete","sessionId":"session"}\n')
            runner.postflight(manifest,root,pinned,controlled,{'mipmapLevels':'2'},before)
            self.assertFalse(manifest['valid'])
            self.assertFalse(manifest['inputsUnchanged'])

    def test_reviewed_default_initial_heap_preserves_launchers_no_xms(self):
        spec={'benchmarkSuite':'full-modpack','jvmInitialHeap':'default','jvmOptions':['-Xmx6144M']}
        self.assertEqual(['-Xmx6144M'],runner.controlled_jvm_options(spec))
        with self.assertRaises(ValueError):runner.controlled_jvm_options({**spec,'jvmOptions':['-Xms1G','-Xmx6144M']})
        with self.assertRaises(ValueError):runner.controlled_jvm_options({**spec,'jvmInitialHeap':'unreviewed'})

    def test_capture_requires_artifact_and_flag_and_uses_owned_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp).resolve()
            spec={'mode':'vanilla','resourceHashingEnabled':True,'mods':[{'id':'observer'},{'id':'texture_correctness'}]}
            runner.validate_mods(spec)
            self.assertEqual(['-Dpackforge.correctness.resourceHashingEnabled=true',
                              '-Dpackforge.correctness.output='+str(root/'texture-pixels.jsonl')],runner.correctness_arguments(spec,root))
            for bad in ({**spec,'resourceHashingEnabled':False},{**spec,'mods':[{'id':'observer'}]}):
                with self.assertRaises(ValueError):runner.validate_mods(bad)
                with self.assertRaises(ValueError):runner.correctness_arguments(bad,root)
            self.assertEqual([],runner.correctness_arguments({'mods':[{'id':'observer'}]},root))
            with self.assertRaises(ValueError):runner.controlled_jvm_options({'benchmarkSuite':'full-modpack','jvmOptions':['-Xms1G','-Xmx4G','-Dpackforge.correctness.output=C:/unreviewed']})


if __name__=='__main__':unittest.main()
