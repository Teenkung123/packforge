import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('runner', Path(__file__).with_name('runner.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class RunnerTest(unittest.TestCase):
    def test_options_snapshot_exact_copy_and_source_preserved(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / 'snapshot.txt'
            contents = b'graphicsPreset:"custom"\r\nlang:th_th\r\nforceUnicodeFont:true\r\n'
            source.write_bytes(contents)
            target = Path(root) / 'options.txt'
            pinned = runner.stage_options({'optionsSnapshot':{'path':str(source), 'sha256':runner.digest(source)}},
                                          target, {'graphicsPreset':'"custom"'})
            self.assertEqual([target], pinned)
            self.assertEqual(contents, target.read_bytes())
            self.assertEqual(contents, source.read_bytes())
            self.assertEqual('th_th', runner.read_options(target)['lang'])

    def test_options_snapshot_controlled_conflict_and_hash_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / 'snapshot.txt'
            source.write_text('graphicsPreset:"fast"\n')
            target = Path(root) / 'options.txt'
            item = {'path':str(source), 'sha256':runner.digest(source)}
            with self.assertRaisesRegex(ValueError, 'controlled'):
                runner.stage_options({'optionsSnapshot':item}, target, {'graphicsPreset':'"custom"'})
            self.assertFalse(target.exists())
            item['sha256'] = '0' * 64
            with self.assertRaisesRegex(ValueError, 'Checksum'):
                runner.stage_options({'optionsSnapshot':item}, target, {})

    def test_snapshot_tampering_outside_controlled_keys_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            game = Path(root)
            options = game / 'options.txt'
            options.write_text('graphicsPreset:"custom"\nlang:en_us\n')
            manifest = {'inputHashes':runner.snapshot([options]), 'exitCode':0, 'timeout':False,
                        'sessionId':'session', 'context':{'optionsSnapshotPinned':True}}
            options.write_text('graphicsPreset:"custom"\nlang:th_th\n')
            (game/'events.jsonl').write_text('{"event":"complete","sessionId":"session"}')
            runner.postflight(manifest, game, [options], [], {'graphicsPreset':'"custom"'})
            self.assertFalse(manifest['valid'])
            self.assertFalse(manifest['inputsUnchanged'])
            self.assertEqual('th_th', manifest['context']['effectiveOptions']['lang'])
            self.assertEqual([{'id':'options.txt','sha256':runner.digest(options)}], manifest['context']['configs'])

    def test_observer_integrity_matches_fixed_dimensions_and_pack(self):
        self.assertEqual(['-Dpackforge.benchmark.expectedWidth=1280',
                          '-Dpackforge.benchmark.expectedHeight=720',
                          '-Dpackforge.benchmark.expectedPackId=file/DevelopRP-Full-1.9.5.zip'],
                         runner.observer_integrity_arguments({'pack':{'id':'DevelopRP-Full-1.9.5.zip'}}))
    def test_flight_recording_only_explicit_profile_and_owned_path(self):
        with tempfile.TemporaryDirectory() as root:
            game = Path(root) / 'fresh-run'
            self.assertEqual([], runner.flight_recording_arguments({}, game))
            self.assertEqual([], runner.flight_recording_arguments({'profilingEnabled':True}, game))
            with self.assertRaises(ValueError): runner.flight_recording_arguments({'flightRecording':True}, game)
            with self.assertRaises(ValueError): runner.flight_recording_arguments({'flightRecording':'true', 'profilingEnabled':True}, game)
            args = runner.flight_recording_arguments({'flightRecording':True, 'profilingEnabled':True}, game)
            self.assertEqual(['-XX:StartFlightRecording=settings=profile,dumponexit=true,filename=' + str((game/'profile.jfr').resolve())], args)
    def test_child_temp_environment_private_and_parent_unchanged(self):
        parent = {'Path':'keep', 'Temp':'old-temp', 'TMP':'old-tmp', 'PRIVATE_TOKEN':'never-record'}
        original = dict(parent)
        environment, recorded = runner.child_environment(parent)
        self.assertEqual(original, parent)
        self.assertEqual('keep', environment['Path'])
        self.assertEqual('never-record', environment['PRIVATE_TOKEN'])
        self.assertEqual({'TEMP','TMP'}, set(recorded))
        self.assertEqual(recorded['TEMP'], recorded['TMP'])
        self.assertTrue(Path(recorded['TEMP']).is_relative_to(runner.ROOT.parent))
        self.assertEqual('modernization-temp', Path(recorded['TEMP']).name)
        self.assertNotIn('Temp', environment)
        self.assertEqual(recorded['TEMP'], environment['TEMP'])
    def test_official_fabric_top_level_library_hash(self):
        self.assertEqual('fabric', runner.library_sha1({'sha1':'fabric'}))
        self.assertEqual('mojang', runner.library_sha1({'downloads':{'artifact':{'sha1':'mojang'}}}))
        self.assertIsNone(runner.library_sha1({'name':'no:checksum:1'}))
    def test_functional_run_options_are_strict(self):
        runner.validate_run_options({})
        runner.validate_run_options({'reloadCount':1, 'profilingEnabled':True, 'resourceHashingEnabled':True})
        for value in (0, 129, True, 1.5, '6'):
            with self.assertRaises(ValueError): runner.validate_run_options({'reloadCount':value})
        for key in ('profilingEnabled', 'resourceHashingEnabled'):
            with self.assertRaises(ValueError): runner.validate_run_options({key:'false'})

    def test_current_graphics_options_missing_is_not_ignored(self):
        options = runner.game_options({'pack':{'id':'x.zip'}})
        self.assertNotIn('graphicsMode', options)
        self.assertEqual('"custom"', options['graphicsPreset'])
        self.assertEqual('false', options['improvedTransparency'])
        for key in ('graphicsPreset', 'improvedTransparency'):
            actual = dict(options)
            del actual[key]
            self.assertFalse(runner.options_match(options, actual))

    def test_afk_throttling_disabled_with_vanilla_setting(self):
        options = runner.game_options({'pack':{'id':'x.zip'}})
        self.assertEqual('"minimized"', options['inactivityFpsLimit'])
        self.assertTrue(runner.options_match(options, dict(options)))
        self.assertFalse(runner.options_match(options, {**options, 'inactivityFpsLimit':'"afk"'}))
        actual = dict(options)
        del actual['inactivityFpsLimit']
        self.assertFalse(runner.options_match(options, actual))

    def test_postflight_reports_owned_changes(self):
        with tempfile.TemporaryDirectory() as root:
            game = Path(root)
            (game / 'config').mkdir()
            item = game / 'config/a.json'
            item.write_text('{}')
            manifest = {'inputHashes':runner.snapshot([item]), 'exitCode':0, 'timeout':False, 'sessionId':'session'}
            item.write_text('{"changed":true}')
            (game / 'config/extra.json').write_text('{}')
            (game / 'options.txt').write_text('graphicsPreset:"fast"\n')
            (game / 'events.jsonl').write_text('{"event":"complete","sessionId":"session"}')
            runner.postflight(manifest, game, [item], [item], {'graphicsPreset':'"custom"'})
            self.assertIs(False, manifest['valid'])
            details = manifest['postflightDiagnostics']
            self.assertEqual(1, details['changedInputCount'])
            self.assertEqual(['config/a.json'], [x.replace('\\','/') for x in details['changedOwnedInputs']])
            self.assertEqual(['graphicsPreset'], details['changedOptionKeys'])
            self.assertEqual(['config/extra.json'], [x.replace('\\','/') for x in details['unexpectedControlledFiles']])

    def test_incompatible_pack_is_explicit_boolean(self):
        spec = {'pack':{'id':'protected.zip'}}
        self.assertEqual('[]', runner.game_options(spec)['incompatibleResourcePacks'])
        spec['acceptIncompatiblePack'] = True
        options = runner.game_options(spec)
        self.assertEqual('["file/protected.zip"]', options['incompatibleResourcePacks'])
        self.assertTrue(runner.options_match(options, dict(options)))
        self.assertFalse(runner.options_match(options, {**options, 'incompatibleResourcePacks':'[]'}))
        for value in ('true', 1, None, []):
            spec['acceptIncompatiblePack'] = value
            with self.assertRaises(ValueError): runner.game_options(spec)

    def test_only_jna_numbered_temporary_natives_excluded(self):
        with tempfile.TemporaryDirectory() as root:
            for name in ('jna123.dll', 'jna.dll', 'jnidispatch.dll', 'jnaABC.dll', 'lwjgl.dll'):
                (Path(root) / name).write_bytes(b'native')
            self.assertEqual({'jna.dll', 'jnidispatch.dll', 'jnaABC.dll', 'lwjgl.dll'},
                             {path.name for path in runner.pinned_natives(root)})

    def test_postflight_missing_input_preserves_process_evidence(self):
        with tempfile.TemporaryDirectory() as root:
            game = Path(root)
            source = game / 'input.jar'
            source.write_bytes(b'pinned')
            manifest = {'inputHashes':runner.snapshot([source]), 'pid':123, 'exitCode':0,
                        'processStartedEpochMillis':123456, 'timeout':False, 'sessionId':'session'}
            source.unlink()
            runner.postflight(manifest, game, [source], [], {})
            self.assertIs(False, manifest['valid'])
            self.assertIs(False, manifest['inputsUnchanged'])
            self.assertEqual(0, manifest['exitCode'])
            self.assertEqual(123, manifest['pid'])
            self.assertEqual(123456, manifest['processStartedEpochMillis'])
            runner.write(game / 'manifest.json', manifest)
            self.assertEqual(manifest, runner.read(game / 'manifest.json'))

    def test_postflight_missing_or_malformed_events_is_failure(self):
        with tempfile.TemporaryDirectory() as root:
            game = Path(root)
            (game / 'options.txt').write_text('pauseOnLostFocus:false\n')
            for content in (None, '{malformed', '[]'):
                if content is not None: (game / 'events.jsonl').write_text(content)
                manifest = {'inputHashes':{}, 'exitCode':0, 'timeout':False, 'sessionId':'session'}
                runner.postflight(manifest, game, [], [], {'pauseOnLostFocus':'false'})
                self.assertIs(False, manifest['valid'])
                self.assertIs(False, manifest['inputsUnchanged'])
                self.assertIn('failureReason', manifest)

    def metadata(self, root, identifier, data):
        path = Path(root) / identifier / (identifier + '.json')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({'id':identifier, **data}), encoding='utf-8')
        return path.resolve()

    def test_inherited_production_profile(self):
        with tempfile.TemporaryDirectory() as root:
            parent = self.metadata(root, '26.2', {'mainClass':'net.minecraft.client.main.Main',
                'downloads':{'client':{'sha1':'abc'}}, 'arguments':{'game':['--username','${auth_player_name}'], 'jvm':['-cp','${classpath}']},
                'libraries':[{'name':'org.example:library:1'}, {'name':'org.example:native:1:windows'}]})
            child = self.metadata(root, '26.2-forge', {'inheritsFrom':'26.2', 'mainClass':'net.minecraftforge.bootstrap.ForgeBootstrap',
                'arguments':{'game':['--launchTarget','forge_client'], 'jvm':['-Dfoo=bar']},
                'libraries':[{'name':'org.example:library:2'}]})
            merged, files, client = runner.load_metadata(child, root)
            self.assertEqual('26.2-forge', merged['id'])
            self.assertEqual('net.minecraftforge.bootstrap.ForgeBootstrap', merged['mainClass'])
            self.assertEqual(['-cp','${classpath}','-Dfoo=bar'], merged['arguments']['jvm'])
            self.assertEqual(['--username','${auth_player_name}','--launchTarget','forge_client'], merged['arguments']['game'])
            self.assertEqual(['org.example:library:2','org.example:native:1:windows'], [x['name'] for x in merged['libraries']])
            self.assertEqual(parent.with_suffix('.jar'), client)
            self.assertEqual([parent, child], files)

    def test_inheritance_cycles_paths_and_missing_root(self):
        with tempfile.TemporaryDirectory() as root:
            a = self.metadata(root, 'a', {'inheritsFrom':'b'})
            self.metadata(root, 'b', {'inheritsFrom':'a'})
            with self.assertRaisesRegex(ValueError, 'cycle'): runner.load_metadata(a, root)
            with self.assertRaisesRegex(ValueError, 'metadataRoot'): runner.load_metadata(a)
            for identifier in ('../escape', 'missing'):
                path = self.metadata(root, 'a', {'inheritsFrom':identifier})
                with self.assertRaises(ValueError): runner.load_metadata(path, root)

    def test_client_selection(self):
        with tempfile.TemporaryDirectory() as root:
            self.metadata(root, '26.2', {'downloads':{'client':{'sha1':'parent'}}})
            self.metadata(root, 'other', {'downloads':{'client':{'sha1':'other'}}})
            child = self.metadata(root, 'child', {'inheritsFrom':'26.2','jar':'other'})
            merged, _, client = runner.load_metadata(child, root)
            self.assertEqual('other', merged['downloads']['client']['sha1'])
            self.assertEqual((Path(root)/'other/other.jar').resolve(), client)
            child = self.metadata(root, 'child', {'inheritsFrom':'26.2','downloads':{'client':{'sha1':'child'}}})
            merged, _, client = runner.load_metadata(child, root)
            self.assertEqual('child', merged['downloads']['client']['sha1'])
            self.assertEqual(child.with_suffix('.jar'), client)

    def test_path_escape_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            for path in ('../escape', '.', str(Path(root).parent)):
                with self.assertRaises(ValueError): runner.beneath(root, path)

    def test_unknown_rules_fail_closed(self):
        with self.assertRaises(ValueError): runner.rules([{'action':'allow', 'features':{'new_option':True}}])
        with self.assertRaises(ValueError): runner.rules([{'action':'allow', 'os':{'unknown':'value'}}])
        self.assertFalse(runner.rules([{'action':'allow', 'features':{'is_demo_user':True}}]))
        self.assertTrue(runner.rules([{'action':'allow', 'features':{'has_custom_resolution':True, 'is_quick_play_realms':None}}]))

    def test_rule_order(self):
        self.assertFalse(runner.rules([{'action':'allow'}, {'action':'disallow', 'os':{'name':'windows'}}]))
        self.assertTrue(runner.rules([{'action':'allow'}, {'action':'disallow', 'os':{'name':'linux'}}]))

    def test_argument_no_shell_expansion(self):
        self.assertEqual(['C:/a b/$(thing)'], runner.arguments(['${dir}'], {'dir':'C:/a b/$(thing)'}))
        with self.assertRaises(ValueError): runner.arguments(['${missing}'], {})

    def test_archive_copied_as_opaque_bytes(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / 'protected.zip'
            source.write_bytes(b'PK deliberately not a readable archive')
            item = {'path':str(source), 'sha256':runner.digest(source)}
            target = Path(root) / 'copy.zip'
            runner.copy_pinned(item, target)
            self.assertEqual(source.read_bytes(), target.read_bytes())
            with self.assertRaises(FileExistsError): runner.copy_pinned(item, target)
            item['sha256'] = '0' * 64
            with self.assertRaises(ValueError): runner.copy_pinned(item, Path(root) / 'bad.zip')

    def test_provision_rejects_origin_and_hash(self):
        for url, hash_value in [('https://example.com/a', 'a'*40), ('https://piston-data.mojang.com/a', '')]:
            with self.assertRaises(ValueError): runner.provision({'files':[{'url':url,'hash':hash_value,'path':'a'}]})

    def test_maven_classifier(self):
        self.assertEqual('a/b/c/1/c-1-windows.zip', runner.library_path({'name':'a.b:c:1:windows@zip'}))

    def test_options_semantics_keep_pack_order(self):
        self.assertTrue(runner.options_match({'resourcePacks':'["vanilla","file/x.zip"]'},
                                             {'resourcePacks':'["vanilla", "file/x.zip"]'}))
        self.assertFalse(runner.options_match({'resourcePacks':'["vanilla","file/x.zip"]'},
                                              {'resourcePacks':'["file/x.zip","vanilla"]'}))


if __name__ == '__main__': unittest.main()
