import copy
import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


analysis, prepare = load('analysis'), load('prepare')
runner = prepare.runner


def fixture(run, ratio=1, run_root=None):
    selected = ['vanilla', 'continuity:default', 'file/DevelopRP-Full-1.9.5.zip']
    modes = analysis.PRIMARY + analysis.REFERENCES
    identity_index = (0 if run['scenario'] == 'menu' else 1) * 100 + run['block'] * 10 + modes.index(run['mode'])
    process_started = 1000000 + identity_index * 1000000
    scene = {'generatedBy':'packbench','mode':'observer-created','worldId':runner.scene_contract.WORLD_ID,
             'seed':runner.scene_contract.SEED,'entityUuids':runner.scene_contract.ENTITY_UUIDS[:],
             'observerSha256':'c'*64,'implementationSha256':'d'*64,'sourceSha256':'e'*64,
             'manifestPath':str(Path('unit-fixture-scene-manifest.json').resolve()),'manifestSha256':'a'*64}
    context = {'benchmarkSuite':'full-modpack', 'scenario':run['scenario'], 'frozenProfileSha256':'a'*64,
               'minecraft':'26.1.2', 'loader':'fabric', 'loaderVersion':'0.19.2',
               'jdk':{'version':'25', 'vendor':'test', 'executableSha256':'a'*64},
               'hardware':{'cpu':'test', 'gpu':'test', 'os':'test', 'ramBytes':4096},
               'jvmArgs':['-Xms1G','-Xmx4G'], 'graphicsSettings':{'width':2560,'height':1440},
               'runtimeEnvironment':{'TEMP':str(analysis.base.RUNTIME_TEMP_ROOT),'TMP':str(analysis.base.RUNTIME_TEMP_ROOT)},
               'optionsSnapshotPinned':True,
               'effectiveOptions':{'lang':'en_us','forceUnicodeFont':'false','japaneseGlyphVariants':'false','textureFiltering':'0',
                                   'mipmapLevels':'2','renderDistance':'8','simulationDistance':'6','fullscreen':'true','graphicsPreset':'"custom"',
                                   'resourcePacks':json.dumps(selected),'inactivityFpsLimit':'"minimized"','enableVsync':'false','maxFps':'260'},
               'orderedSelectedPackIds':selected, 'orderedPacks':[{'id':'DevelopRP-Full-1.9.5.zip','sha256':'b'*64}],
               'artifacts':[{'id':'observer','sha256':'c'*64},{'id':'fo/mod.jar','sha256':'f'*64}],
               'configs':[{'id':'options.txt','sha256':'e'*64},{'id':'config/iris.properties','sha256':'e'*64}],
               'shaderPacks':[{'id':'ComplementaryUnbound_r5.7.1.zip','sha256':'c'*64}],
               'qualityContract':{'matched':True,'evidenceSha256':'e'*64}, 'configurationRole':run['mode'],
               'filesystemCache':'uncontrolled','profilingEnabled':False,'resourceHashingEnabled':False,
               'generatedScene':None if run['scenario']=='menu' else scene}
    if run['mode'] in ('saved','configuration','candidate','combined'): context['artifacts'].append({'id':'packforge','sha256':'d'*64})
    if run['mode'] in ('quickpack','quickpack_default','combined'): context['artifacts'].append({'id':'quickpack','sha256':'e'*64})
    if run_root is not None:
        def write(relative, data):
            path = run_root / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
            return hashlib.sha256(data).hexdigest()
        hashes = {}
        hashes['mods/observer.jar'] = write('mods/observer.jar', b'observer')
        hashes['mods/mod.jar'] = write('mods/mod.jar', b'companion')
        for item in context['artifacts']:
            if item['id'] == 'observer': item['sha256'] = hashes['mods/observer.jar']
            elif item['id'] == 'fo/mod.jar': item['sha256'] = hashes['mods/mod.jar']
        scene['observerSha256'] = hashes['mods/observer.jar']
        if run['mode'] in ('saved','configuration','candidate','combined'):
            hashes['mods/packforge.jar'] = write('mods/packforge.jar', b'packforge')
            next(item for item in context['artifacts'] if item['id'] == 'packforge')['sha256'] = hashes['mods/packforge.jar']
        if run['mode'] in ('quickpack','quickpack_default','combined'):
            hashes['mods/quickpack.jar'] = write('mods/quickpack.jar', b'quickpack')
            next(item for item in context['artifacts'] if item['id'] == 'quickpack')['sha256'] = hashes['mods/quickpack.jar']
        hashes['resourcepacks/DevelopRP-Full-1.9.5.zip'] = write('resourcepacks/DevelopRP-Full-1.9.5.zip', b'resource')
        hashes['shaderpacks/ComplementaryUnbound_r5.7.1.zip'] = write('shaderpacks/ComplementaryUnbound_r5.7.1.zip', b'shader')
        hashes['config/iris.properties'] = write('config/iris.properties', b'iris')
        hashes['options.txt'] = write('options.txt', b'options')
        context['configs'] = [{'id':'options.txt','sha256':hashes['options.txt']},{'id':'config/iris.properties','sha256':hashes['config/iris.properties']}]
        context['shaderPacks'][0]['sha256'] = hashes['shaderpacks/ComplementaryUnbound_r5.7.1.zip']
        mods = [{'id':'observer','filename':'observer.jar','sha256':hashes['mods/observer.jar']},
                {'id':'fo/mod.jar','filename':'mod.jar','sha256':hashes['mods/mod.jar']}]
        if run['mode'] in ('saved','configuration','candidate','combined'): mods.append({'id':'packforge','filename':'packforge.jar','sha256':hashes['mods/packforge.jar']})
        if run['mode'] in ('quickpack','quickpack_default','combined'): mods.append({'id':'quickpack','filename':'quickpack.jar','sha256':hashes['mods/quickpack.jar']})
        def ident(name, key): return {'id':name,'sha256':hashes[key]}
        spec = {'mode':run['mode'],'scenario':run['scenario'],'workload':run['workload'],'mods':mods,
                'resourcePacks':[ident('DevelopRP-Full-1.9.5.zip','resourcepacks/DevelopRP-Full-1.9.5.zip')],
                'shaderPacks':[ident('ComplementaryUnbound_r5.7.1.zip','shaderpacks/ComplementaryUnbound_r5.7.1.zip')],
                'configs':[ident('iris.properties','config/iris.properties')],
                'optionsSnapshot':{'path':'','sha256':hashes['options.txt']}}
        frozen = {'schema':1,'suite':'full-modpack','private':True,
                  'files':[{'id':'mods/mod.jar','path':'','sha256':hashes['mods/mod.jar'],'bytes':9},
                           {'id':'resourcepacks/DevelopRP-Full-1.9.5.zip','path':'','sha256':hashes['resourcepacks/DevelopRP-Full-1.9.5.zip'],'bytes':8},
                           {'id':'shaderpacks/ComplementaryUnbound_r5.7.1.zip','path':'','sha256':hashes['shaderpacks/ComplementaryUnbound_r5.7.1.zip'],'bytes':6},
                           {'id':'config/iris.properties','path':'','sha256':hashes['config/iris.properties'],'bytes':4},
                           {'id':'options.txt','path':'','sha256':hashes['options.txt'],'bytes':7}],
                  'optimizerFiles':{'packforge':'mods/packforge.jar','quickpack':'mods/quickpack.jar'}}
        reviewed = {'schema':1,'suite':'full-modpack','sourceFrozenProfileSha256':'',**{k:spec[k] for k in ('mode','scenario','workload')},
                    'mods':[dict(item, path='') for item in spec['mods']],
                    'resourcePacks':[dict(item, path='') for item in spec['resourcePacks']],
                    'shaderPacks':[dict(item, path='') for item in spec['shaderPacks']],
                    'configs':[dict(item, path='') for item in spec['configs']],
                    'optionsSnapshot':{'path':'','sha256':hashes['options.txt']},'deviations':[]}
        def bind(name, value):
            raw = (json.dumps(value, sort_keys=True)+'\n').encode(); path=run_root/name; path.write_bytes(raw)
            return {'path':str(path),'sha256':hashlib.sha256(raw).hexdigest()}
        frozen_binding=bind('frozen.json',frozen); reviewed['sourceFrozenProfileSha256']=frozen_binding['sha256']; reviewed_binding=bind('reviewed.json',reviewed)
        context['frozenProfileSha256']=frozen_binding['sha256']
        context['inputSpec']={**spec,'frozenProfileManifest':frozen_binding,'reviewedInputManifest':reviewed_binding}
        input_hashes = {str((run_root / relative).resolve()): digest for relative,digest in hashes.items()}
    else:
        input_hashes = None
    manifest = {'schema':1, **{key:run[key] for key in ('runId','cellId','mode','block','workload')},
                'sessionId':run['runId'], 'pid':42000 + identity_index, 'processStartedEpochMillis':process_started,
                'exitCode':0,'timeout':False,'valid':True,'inputsUnchanged':True,'eventsFile':'events.jsonl','context':context}
    if input_hashes is not None: manifest['inputHashes'] = input_hashes
    events, elapsed, sequence = [], 1000, 1
    world, menu_ack, world_ack, probe_generation = False, 0, 0, 0
    def event(kind,index,generation,**fields):
        nonlocal elapsed, sequence
        sequence += 1
        events.append({'schema':1,'observerProtocol':3,'scenario':run['scenario'],'sessionId':run['runId'],
                       'discoveryOnly':False,'overlayPresent':False,'titleFading':False,
                       'menuRendered':not world,'worldRendered':world,
                       'menuInputDispatchGeneration':menu_ack,'worldInputDispatchGeneration':world_ack,
                       'inputProbeGeneration':probe_generation,'inputProbeAttempts':1 if probe_generation else 0,
                       'inputProbeChangedActivity':False,'resourceReloadCount':generation,
                       'event':kind,'reloadIndex':index,'windowActive':True,'framebufferWidth':2560,'framebufferHeight':1440,
                       'epochMillis':process_started+elapsed,'nanoTime':elapsed*1000000,'jvmUptimeMillis':elapsed,
                       'resourceGeneration':generation,'renderedResourceGeneration':generation,'activeResourceReloads':0,
                       'inputTickSequence':sequence,'renderSequence':sequence,'requestResourceGenerations':1,
                       **fields})
        if run['scenario']=='inworld':
            events[-1].update(sceneId=scene['worldId'],sceneSeed=scene['seed'],sceneImplementationSha256=scene['implementationSha256'],
                              sceneSourceSha256=scene['sourceSha256'],sceneEntityUuids=scene['entityUuids'][:],
                              sceneObservedEntityUuids=scene['entityUuids'][:] if world else [])
        if kind in ('ready','world_ready','frame_ready'): events[-1]['activePackIds']=selected[:]
    event('resource_reload_started',-1,1,windowActive=False,framebufferWidth=-1,framebufferHeight=-1,activeResourceReloads=1,renderedResourceGeneration=0)
    elapsed+=100
    event('resource_reload_complete',-1,1,renderedResourceGeneration=0)
    event('resources_rendered',-1,1,titleFading=True,overlayPresent=True)
    event('overlay_cleared',-1,1,titleFading=True)
    elapsed+=1000
    menu_ack=probe_generation=1
    event('input_ready',-1,1)
    event('ready',-1,1)
    if run['scenario']=='inworld':
        event('world_loading',-1,1)
        elapsed+=16000
        sequence+=120
        world=True;world_ack=1
        event('world_ready',-1,1)
    for index in range(6):
        elapsed+=1000
        event('reload_requested',index,index+1)
        event('resource_reload_started',index,index+2,activeResourceReloads=1,renderedResourceGeneration=index+1)
        elapsed+=int(90*ratio)
        event('resource_reload_complete',index,index+2,renderedResourceGeneration=0,overlayPresent=True)
        event('reload_complete',index,index+2,renderedResourceGeneration=0,overlayPresent=True)
        elapsed+=int(10*ratio)
        event('resources_rendered',index,index+2,overlayPresent=True)
        if world:world_ack=index+2
        else:menu_ack=probe_generation=index+2
        event('input_ready',index,index+2,overlayPresent=True)
        event('frame_ready',index,index+2,overlayPresent=True)
        elapsed+=2000
        event('overlay_cleared',index,index+2)
    event('complete',5,7)
    return manifest,events


class PreparationTests(unittest.TestCase):
    def test_freeze_only_allowlist_and_never_parses_protected_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); profile=root/'original'; private=root/'private'
            for directory in (*prepare.DIRECTORIES,'saves','accounts'): (profile/directory).mkdir(parents=True)
            protected=profile/'resourcepacks'/prepare.PROTECTED_NAME
            protected.write_bytes(b'not-even-a-zip\x00unusual protected headers')
            (profile/'mods'/'packforge.jar').write_bytes(b'pf')
            (profile/'mods'/'quick.jar.disabled').write_bytes(b'qp')
            (profile/'mods'/'sodium.jar').write_bytes(b'sodium')
            (profile/'config'/'unknown.json').write_bytes(b'{"keep":true}')
            (profile/'options.txt').write_text('resourcePacks:["vanilla","file/'+prepare.PROTECTED_NAME+'"]\n')
            (profile/'accounts'/'private.json').write_text('must never read')
            (profile/'saves'/'private.dat').write_text('must never read')
            with patch.object(runner,'ROOT',private),patch.object(prepare,'PROTECTED_SHA256',runner.digest(protected)):
                result=prepare.freeze(profile,private/'snapshot','packforge.jar','quick.jar.disabled')
                ids={item['id'] for item in result['files']}
                self.assertFalse(any(item.startswith(('saves/','accounts/')) for item in ids))
                self.assertEqual(protected.read_bytes(),(private/'snapshot/inputs/resourcepacks'/prepare.PROTECTED_NAME).read_bytes())
                with self.assertRaisesRegex(ValueError,'new isolated'): prepare.freeze(profile,private/'snapshot','packforge.jar','quick.jar.disabled')

    def test_control_enables_mips_without_resize_retry_or_uv_changes(self):
        saved={'configVersion':13,'largeAtlasFixerEnabled':False,'atlasMipParallelEnabled':False,'atlasCapEnabled':True,
               'modelUvTransparencyClampEnabled':True,'loadingScreenFadeOutDisabled':False,'unknown':{'keep':1}}
        before=copy.deepcopy(saved)
        control,preview=prepare.configuration_control(saved)
        self.assertEqual(before,saved)
        self.assertTrue(control['largeAtlasFixerEnabled'] and control['atlasMipParallelEnabled'])
        for key in ('atlasCapEnabled','atlasRetryEnabled','modelUvTransparencyClampEnabled','fontBitmapProviderCacheEnabled',
                    'loaderZipPoolEnabled','resourceReadReuseEnabled','startupExecutorTuningEnabled'):
            self.assertFalse(control[key])
        self.assertEqual(saved['unknown'],control['unknown'])
        self.assertFalse(control['loadingScreenFadeOutDisabled'])
        self.assertTrue(preview)
        for version in (None,12,14):
            with self.assertRaises(ValueError):prepare.configuration_control({'configVersion':version})

    def test_dynamic_changes_only_benchmark_throttling(self):
        source={'idle':{'condition':'none'},'states':{'unfocused':{'frame_rate_target':30,'unrelated':'keep'}},'download_natives':False}
        result=prepare.disable_dynamic_throttling(source)
        self.assertEqual(30,source['states']['unfocused']['frame_rate_target'])
        self.assertEqual('keep',result['states']['unfocused']['unrelated'])
        self.assertEqual(-1,result['states']['unfocused']['frame_rate_target'])
        self.assertFalse(result['states']['invisible']['run_garbage_collector'])
        self.assertFalse(result['download_natives'])


class RunnerTests(unittest.TestCase):
    def test_combined_only_explicit_full_profile(self):
        spec={'benchmarkSuite':'full-modpack','mode':'combined','mods':[{'id':i} for i in ('observer','packforge','quickpack','sodium')]}
        runner.validate_mods(spec)
        for mode in ('candidate','quickpack','vanilla'):
            with self.assertRaises(ValueError):runner.validate_mods({**spec,'mode':mode})
        with self.assertRaises(ValueError):runner.validate_mods({**spec,'benchmarkSuite':'primary'})

    def test_full_profile_explicit_dimensions_and_jvm(self):
        spec={'benchmarkSuite':'full-modpack','framebuffer':{'width':2560,'height':1440},'jvmOptions':['-Xms1G','-Xmx4G','-XX:+UseG1GC']}
        self.assertEqual((2560,1440),runner.dimensions(spec))
        self.assertEqual(spec['jvmOptions'],runner.controlled_jvm_options(spec))
        for bad in ({},{'width':0,'height':1440},{'width':True,'height':1440}):
            with self.assertRaises(ValueError):runner.dimensions({**spec,'framebuffer':bad})
        for arg in ('-javaagent:foreign.jar','-Dpackforge.benchmark.enabled=false','MainClass','-Xmx8G'):
            with self.assertRaises(ValueError):runner.controlled_jvm_options({**spec,'jvmOptions':spec['jvmOptions']+[arg]})

    def test_snapshot_preserves_full_fo_quality_and_selected_order(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'options.txt'
            values={'mipmapLevels':'2','renderDistance':'8','simulationDistance':'6','graphicsPreset':'"custom"','fullscreen':'true',
                    'resourcePacks':'["vanilla","continuity:default","file/DevelopRP-Full-1.9.5.zip"]','lang':'en_us','shaderUnknown':'keep'}
            path.write_text(''.join(k+':'+v+'\n' for k,v in values.items()))
            spec={'benchmarkSuite':'full-modpack','optionsSnapshot':{'path':str(path),'sha256':runner.digest(path)},'orderedPackIds':json.loads(values['resourcePacks'])}
            self.assertEqual(values,runner.game_options(spec))
            with self.assertRaises(ValueError):runner.game_options({**spec,'orderedPackIds':list(reversed(spec['orderedPackIds']))})


class AnalysisTests(unittest.TestCase):
    def test_normalization_audit_cannot_hide_unreviewed_raw_changes(self):
        run=next(r for r in analysis.make_schedule()['runs'] if r['mode']=='candidate')
        manifest,_=fixture(run)
        manifest['context']['normalizationPolicy']=analysis.normalization.POLICY
        manifest['rawInputsUnchanged']=False
        manifest['inputHashes']={}
        manifest['inputHashesAfter']={}
        manifest['normalizationAudit']=[]
        for index,(relative,rule) in enumerate(analysis.normalization.RULES.items()):
            path='C:/private/fresh-run/'+relative
            old='a'*64;new='b'*64 if index==0 else old
            manifest['inputHashes'][path]=old;manifest['inputHashesAfter'][path]=new
            entry={'id':relative,'rule':rule,'beforeSha256':old,'afterSha256':new,
                   'canonicalBeforeSha256':'c'*64,'canonicalAfterSha256':'c'*64,
                   'accepted':True,'rawChanged':old!=new,'rawDiff':'date-only change' if old!=new else ''}
            if relative=='config/sodium-options.json':entry['notificationPromptChange']={'before':False,'after':False,'fingerprintChanged':False}
            manifest['normalizationAudit'].append(entry)
        run_root=Path('C:/private/fresh-run')
        analysis.validate_normalization(manifest,run_root)
        for mutate in (lambda m:m['inputHashesAfter'].update({'C:/private/fresh-run/unreviewed.jar':'d'*64}),
                       lambda m:m['normalizationAudit'][0].update(canonicalAfterSha256='d'*64),
                       lambda m:m['normalizationAudit'][0].update(rawDiff=''),
                       lambda m:m.update(rawInputsUnchanged=True),
                       lambda m:m['normalizationAudit'][0].update(id='config/packforge.json')):
            invalid=copy.deepcopy(manifest);mutate(invalid)
            with self.assertRaises(ValueError):analysis.validate_normalization(invalid,run_root)

    def test_normalization_binds_to_run_root_with_nested_duplicate_path(self):
        run=next(r for r in analysis.make_schedule()['runs'] if r['mode']=='candidate')
        manifest,_=fixture(run)
        manifest['context']['normalizationPolicy']=analysis.normalization.POLICY
        manifest['rawInputsUnchanged']=True
        manifest['inputHashes']={}
        manifest['inputHashesAfter']={}
        manifest['normalizationAudit']=[]
        run_root=Path('C:/private/fresh-run')
        for relative,rule in analysis.normalization.RULES.items():
            path=str(run_root/relative)
            value='a'*64
            manifest['inputHashes'][path]=value
            manifest['inputHashesAfter'][path]=value
            manifest['normalizationAudit'].append({'id':relative,'rule':rule,'beforeSha256':value,'afterSha256':value,
                                                   'canonicalBeforeSha256':'c'*64,'canonicalAfterSha256':'c'*64,
                                                   'accepted':True,'rawChanged':False,'rawDiff':''})
            if relative=='config/sodium-options.json':
                manifest['normalizationAudit'][-1]['notificationPromptChange']={'before':False,'after':False,'fingerprintChanged':False}
        nested=str(run_root/'config/modpack_defaults/config/iris.properties')
        manifest['inputHashes'][nested]='d'*64
        manifest['inputHashesAfter'][nested]='d'*64
        analysis.validate_normalization(manifest,run_root)

    def test_normalization_requires_actual_run_root(self):
        run=next(r for r in analysis.make_schedule()['runs'] if r['mode']=='candidate')
        manifest,_=fixture(run)
        manifest['context']['normalizationPolicy']=analysis.normalization.POLICY
        with self.assertRaisesRegex(ValueError,'actual run root'):
            analysis.validate_normalization(manifest)

    def test_schedule_ten_gating_three_reference_and_balanced_primary_order(self):
        schedule=analysis.make_schedule()
        analysis.validate_schedule(schedule)
        self.assertEqual(112,len(schedule['runs']))
        self.assertEqual(schedule,analysis.make_schedule())
        self.assertNotEqual(schedule,analysis.make_schedule(1))
        for cell in schedule['cells']:
            for mode,count in cell['processesPerMode'].items():
                self.assertEqual(count,len([r for r in schedule['runs'] if r['cellId']==cell['cellId'] and r['mode']==mode]))
                if mode in analysis.PRIMARY:
                    positions=[0]*5
                    for block in range(10):
                        order=[r['mode'] for r in schedule['runs'] if r['cellId']==cell['cellId'] and r['block']==block and r['mode'] in analysis.PRIMARY]
                        positions[order.index(mode)]+=1
                    self.assertEqual([2]*5,positions)
        schedule['runs'].pop()
        with self.assertRaises(ValueError):analysis.validate_schedule(schedule)

    def test_noninferiority_rejects_point_ci_and_p95_failures(self):
        self.assertTrue(analysis.noninferiority([100]*10,[104]*10,1,100)['passed'])
        self.assertFalse(analysis.noninferiority([100]*10,[106]*10,1,100)['passed'])
        # Median alone passes; uncertainty and tail do not.
        result=analysis.noninferiority([100]*10,[100]*6+[150]*4,1,2000)
        self.assertEqual(1,result['medianRatio'])
        self.assertFalse(result['passed'])
        self.assertGreater(result['ratioCi95'][1],1.05)
        with self.assertRaises(ValueError):analysis.noninferiority([100]*3,[100]*3,1)

    def test_both_scenarios_require_generation_and_new_frame(self):
        for scenario in analysis.SCENARIOS:
            run=next(r for r in analysis.make_schedule()['runs'] if r['scenario']==scenario and r['mode']=='candidate')
            manifest,events=fixture(run)
            samples=analysis.validate_process(manifest,events,run)
            self.assertEqual(5,len(samples['measuredReloadsMs']))
            self.assertEqual(0,samples['extraReloadCount'])
            startup_kind='ready' if scenario=='menu' else 'world_ready'
            startup_completion=next(e for e in events if e['event']=='resource_reload_complete' and e['reloadIndex']==-1)
            changed=copy.deepcopy(events)
            startup=next(e for e in changed if e['event']==startup_kind)
            startup['inputTickSequence']=startup_completion['inputTickSequence']
            startup['renderSequence']=startup_completion['renderSequence']
            with self.assertRaisesRegex(ValueError,'old input/render activity'):
                analysis.validate_process(manifest,changed,run)
            for mutation in (lambda e:e.update(renderedResourceGeneration=1),lambda e:e.update(activeResourceReloads=1),
                             lambda e:e.update(observerProtocol=1),lambda e:e.update(observerProtocol=2),
                             lambda e:e.update(requestResourceGenerations=2),lambda e:e.update(inputTickSequence=1),
                             lambda e:e.update(windowActive=False),lambda e:e.update(inputProbeChangedActivity=True),
                             lambda e:e.update(reloadIndex=True),lambda e:e.update(discoveryOnly=True)):
                changed=copy.deepcopy(events)
                mutation(next(e for e in changed if e['event']=='frame_ready'))
                with self.assertRaises(ValueError):analysis.validate_process(manifest,changed,run)

    def test_allocated_rrls_overlay_does_not_replace_real_input_readiness(self):
        run=next(r for r in analysis.make_schedule()['runs'] if r['scenario']=='menu' and r['mode']=='candidate')
        manifest,events=fixture(run)
        samples=analysis.validate_process(manifest,events,run)
        self.assertTrue(all(e['overlayPresent'] for e in events if e['event']=='frame_ready'))
        self.assertEqual([2000]*5,samples['measuredOverlayTailMs'])
        self.assertLess(samples['reloadMs'],samples['reloadOverlayClearedMs'])
        # Overlay may clear before or after input; neither ordering proves input.
        for mutation in (lambda e:e.update(menuInputDispatchGeneration=1),lambda e:e.update(titleFading=True),
                         lambda e:e.update(inputProbeGeneration=1),lambda e:e.update(inputProbeAttempts=0),
                         lambda e:e.update(menuRendered=False)):
            changed=copy.deepcopy(events)
            mutation(next(e for e in changed if e['event']=='input_ready' and e['reloadIndex']==0))
            with self.assertRaises(ValueError):analysis.validate_process(manifest,changed,run)
        for kind in ('resources_rendered','input_ready','overlay_cleared'):
            changed=copy.deepcopy(events)
            changed.remove(next(e for e in changed if e['event']==kind and e['reloadIndex']==0))
            with self.assertRaises(ValueError):analysis.validate_process(manifest,changed,run)

    def test_world_requires_natural_world_ack_and_exact_entity_identities(self):
        run=next(r for r in analysis.make_schedule()['runs'] if r['scenario']=='inworld' and r['mode']=='candidate')
        manifest,events=fixture(run)
        analysis.validate_process(manifest,events,run)
        for mutation in (lambda e:e.update(worldInputDispatchGeneration=1),lambda e:e.update(worldRendered=False),
                         lambda e:e.update(sceneObservedEntityUuids=[]),lambda e:e.update(sceneObservedEntityUuids=['unrelated']),
                         lambda e:e.update(sceneSeed=e['sceneSeed']+1),lambda e:e.update(sceneImplementationSha256='0'*64),
                         lambda e:e.update(sceneSourceSha256='0'*64),lambda e:e.update(inputProbeAttempts=2)):
            changed=copy.deepcopy(events)
            mutation(next(e for e in changed if e['event']=='frame_ready' and e['reloadIndex']==0))
            with self.assertRaises(ValueError):analysis.validate_process(manifest,changed,run)
        changed=copy.deepcopy(events)
        next(e for e in changed if e['event']=='world_ready')['worldRendered']=False
        with self.assertRaises(ValueError):analysis.validate_process(manifest,changed,run)

    def test_complete_analysis_and_context_mismatch(self):
        schedule=analysis.make_schedule()
        with tempfile.TemporaryDirectory() as temp:
            paths=[]
            for run in schedule['runs']:
                path=Path(temp)/run['runId'];path.mkdir()
                manifest,events=fixture(run, run_root=path)
                (path/'manifest.json').write_text(json.dumps(manifest))
                (path/'events.jsonl').write_text('\n'.join(json.dumps(e) for e in events))
                paths.append(path/'manifest.json')
            report=analysis.analyze(schedule,paths,100)
            self.assertTrue(report['passed'],report['cells'][0]['problems'])
            first=json.loads(paths[0].read_text())
            duplicate=json.loads(paths[1].read_text())
            duplicate['pid']=first['pid']
            duplicate['processStartedEpochMillis']=first['processStartedEpochMillis']
            paths[1].write_text(json.dumps(duplicate))
            report=analysis.analyze(schedule,paths,100)
            self.assertFalse(report['passed'])
            self.assertTrue(any('OS process identity reused' in failure['error'] for failure in report['failures']))
            paths[1].write_text(json.dumps(fixture(schedule['runs'][1])[0]))
            # Keep candidate point estimates unchanged; missing fade equivalence is still not a pass.
            path=next(p for p in paths if '-candidate-' in p.parent.name)
            manifest=json.loads(path.read_text());manifest['context']['qualityContract']['matched']=False
            path.write_text(json.dumps(manifest))
            report=analysis.analyze(schedule,paths,100)
            self.assertFalse(report['passed'])
            self.assertTrue(any('matched quality' in p for c in report['cells'] for p in c['problems']))

    def test_headline_unbound_input_is_rejected(self):
        run = next(r for r in analysis.make_schedule()['runs'] if r['mode'] == 'candidate')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / run['runId']; root.mkdir()
            manifest, events = fixture(run, run_root=root)
            del manifest['context']['inputSpec']
            (root / 'manifest.json').write_text(json.dumps(manifest)); (root / 'events.jsonl').write_text('\n'.join(json.dumps(e) for e in events))
            report = analysis.analyze(analysis.make_schedule(), [root / 'manifest.json'], 10)
            self.assertFalse(report['passed']); self.assertIn('requires bound inputSpec', report['failures'][0]['error'])

    def test_headline_dropped_companion_is_rejected_by_binding(self):
        run = next(r for r in analysis.make_schedule()['runs'] if r['mode'] == 'candidate')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / run['runId']; root.mkdir()
            manifest, events = fixture(run, run_root=root)
            manifest['context']['inputSpec']['mods'] = [i for i in manifest['context']['inputSpec']['mods'] if i['id'] != 'fo/mod.jar']
            (root / 'manifest.json').write_text(json.dumps(manifest)); (root / 'events.jsonl').write_text('\n'.join(json.dumps(e) for e in events))
            report = analysis.analyze(analysis.make_schedule(), [root / 'manifest.json'], 10)
            self.assertFalse(report['passed']); self.assertIn('headline input binding rejected', report['failures'][0]['error'])

    def test_headline_frozen_lineage_mismatch_is_rejected(self):
        run = next(r for r in analysis.make_schedule()['runs'] if r['mode'] == 'candidate')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / run['runId']; root.mkdir()
            manifest, events = fixture(run, run_root=root)
            manifest['context']['frozenProfileSha256'] = '0' * 64
            (root / 'manifest.json').write_text(json.dumps(manifest)); (root / 'events.jsonl').write_text('\n'.join(json.dumps(e) for e in events))
            report = analysis.analyze(analysis.make_schedule(), [root / 'manifest.json'], 10)
            self.assertFalse(report['passed']); self.assertIn('lineage mismatch', report['failures'][0]['error'])


if __name__=='__main__':unittest.main()
