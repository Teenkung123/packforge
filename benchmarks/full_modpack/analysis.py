"""FO menu/in-world noninferiority gates; separate from existing six-cell gates."""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import random
import statistics
import sys

SPEC = importlib.util.spec_from_file_location('packbench_analysis', Path(__file__).parents[1] / 'analysis/benchmark.py')
base = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(base)
NORMALIZER_SPEC = importlib.util.spec_from_file_location('packbench_normalization', Path(__file__).parents[1] / 'runtime/normalization.py')
normalization = importlib.util.module_from_spec(NORMALIZER_SPEC)
NORMALIZER_SPEC.loader.exec_module(normalization)
SCENE_SPEC = importlib.util.spec_from_file_location('packbench_scene_contract', Path(__file__).parents[1] / 'runtime/scene_contract.py')
scene_contract = importlib.util.module_from_spec(SCENE_SPEC)
SCENE_SPEC.loader.exec_module(scene_contract)
INPUT_BINDING_SPEC = importlib.util.spec_from_file_location('packbench_input_binding', Path(__file__).parents[1] / 'runtime/input_binding.py')
input_binding = importlib.util.module_from_spec(INPUT_BINDING_SPEC)
INPUT_BINDING_SPEC.loader.exec_module(input_binding)
require = base.require


def digest_file(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


PRIMARY = ('saved', 'configuration', 'candidate', 'quickpack', 'combined')
REFERENCES = ('vanilla', 'quickpack_default')
SCENARIOS = ('menu', 'inworld')
METRICS = ('startupMs', 'reloadMs', 'reloadCompleteMs', 'reloadResourcesRenderedMs', 'reloadInputReadyMs', 'reloadOverlayClearedMs')


def make_schedule(seed=260910):
    require(type(seed) is int, 'seed must be an integer')
    rng = random.Random(seed)
    cells, runs = [], []
    for scenario in SCENARIOS:
        modes = list(PRIMARY)
        rng.shuffle(modes)
        cell_id = '26.1.2-fabric-fabulously_optimized-' + scenario
        cell = {'cellId':cell_id, 'minecraft':'26.1.2', 'loader':'fabric', 'loaderVersion':'0.19.2',
                'workload':'fabulously_optimized', 'scenario':scenario,
                'processesPerMode':{mode:10 if mode in PRIMARY else 3 for mode in (*PRIMARY, *REFERENCES)}}
        cells.append(cell)
        for block in range(10):
            shift = block % len(modes)
            order = modes[shift:] + modes[:shift]
            if block in (0, 4, 9):
                occurrence = (0, 4, 9).index(block)
                order.insert((occurrence * 2) % 6, 'vanilla')
                order.insert((occurrence * 2 + 3) % 7, 'quickpack_default')
            for mode in order:
                runs.append({**{key:cell[key] for key in ('cellId', 'minecraft', 'loader', 'workload', 'scenario')},
                             'runId':f'{cell_id}-{mode}-{block:02d}', 'mode':mode, 'block':block,
                             'primingReloads':1, 'measuredReloads':5})
    return {'schema':1, 'suite':'full-modpack', 'seed':seed, 'filesystemCache':'uncontrolled',
            'primaryBlocks':10, 'referenceBlocks':3, 'cells':cells, 'runs':runs}


def validate_schedule(value):
    require(isinstance(value, dict) and value == make_schedule(value.get('seed')), 'schedule differs from complete seeded FO design')


def noninferiority(reference, candidate, seed, iterations=10000):
    require(len(reference) == len(candidate) == 10, 'noninferiority requires ten paired process samples')
    require(type(iterations) is int and iterations > 0, 'invalid bootstrap iterations')
    require(all(type(x) in (int, float) and math.isfinite(x) and x > 0 for x in reference + candidate), 'nonpositive/nonfinite process sample')
    rng = random.Random(seed)
    ratios = []
    for _ in range(iterations):
        indexes = [rng.randrange(10) for _ in range(10)]
        ratios.append(statistics.median(candidate[i] for i in indexes) / statistics.median(reference[i] for i in indexes))
    median_ref, median_candidate = statistics.median(reference), statistics.median(candidate)
    ratio = median_candidate / median_ref
    ci = [base.percentile(ratios, .025), base.percentile(ratios, .975)]
    p95_ref, p95_candidate = base.percentile(reference, .95), base.percentile(candidate, .95)
    return {'samplesPerMode':10, 'referenceMedianMs':median_ref, 'candidateMedianMs':median_candidate,
            'savedMs':median_ref - median_candidate, 'medianRatio':ratio, 'ratioCi95':ci,
            'referenceP95Ms':p95_ref, 'candidateP95Ms':p95_candidate, 'p95Ratio':p95_candidate / p95_ref,
            'margin':1.05, 'passed':ratio <= 1.05 and ci[1] <= 1.05 and p95_candidate / p95_ref <= 1.05}


def validate_context(context, run):
    # Reuse strict hardware, JVM, full options, input hashing, environment, and
    # headline instrumentation checks without weakening primary six-cell rules.
    aliases = {'saved':'current', 'configuration':'corrected', 'quickpack_default':'quickpack'}
    normalized_run = {**run, 'mode':aliases.get(run['mode'], run['mode'])}
    checked_context = context
    if run['mode'] == 'combined':
        checked_context = {**context, 'artifacts':[a for a in context.get('artifacts', []) if a.get('id') != 'quickpack']}
        normalized_run['mode'] = 'candidate'
    base.validate_context(checked_context, normalized_run)
    require(context.get('benchmarkSuite') == 'full-modpack' and context.get('scenario') == run['scenario'], 'wrong benchmark suite/scenario')
    require(context['loaderVersion'] == '0.19.2', 'FO loader version changed')
    require(base.SHA256.fullmatch(context.get('frozenProfileSha256', '')), 'missing frozen profile identity')
    require(context.get('normalizationPolicy') in (None, normalization.POLICY), 'unreviewed output normalization policy')
    ids = {a['id'] for a in context['artifacts']}
    require('texture_correctness' not in ids, 'correctness capture artifact present in headline run')
    expected = ({'packforge'} if run['mode'] in ('saved', 'configuration', 'candidate') else
                {'quickpack'} if run['mode'] in ('quickpack', 'quickpack_default') else
                {'packforge','quickpack'} if run['mode'] == 'combined' else set())
    require(ids & {'packforge','quickpack'} == expected, 'wrong full-modpack optimizer set')
    require(bool(ids - {'packforge','quickpack','observer'}), 'missing frozen companion mods')
    for key, value in {'mipmapLevels':'2', 'renderDistance':'8', 'simulationDistance':'6', 'fullscreen':'true', 'graphicsPreset':'"custom"'}.items():
        require(context['effectiveOptions'].get(key) == value, 'frozen FO visual setting changed: ' + key)
    selected = context.get('orderedSelectedPackIds')
    require(isinstance(selected, list) and selected == base.parse_json(context['effectiveOptions']['resourcePacks']), 'selected pack order not pinned')
    base.hash_list(context.get('shaderPacks'), 'shaderPacks', nonempty=True)
    require(any(p['id'] == 'ComplementaryUnbound_r5.7.1.zip' for p in context['shaderPacks']), 'frozen shader archive missing')
    require(any(p['id'] == 'config/iris.properties' for p in context['configs']), 'Iris shader settings not pinned')
    if run['scenario'] == 'inworld':
        observer_sha = next(item['sha256'] for item in context['artifacts'] if item['id'] == 'observer')
        scene_contract.validate_identity(context.get('generatedScene'), observer_sha)
    else:
        require(context.get('generatedScene') is None, 'menu run unexpectedly contains scene')


def validate_process(manifest, events, run, run_root=None):
    require(isinstance(manifest, dict) and type(manifest.get('schema')) is int and manifest['schema'] == 1, 'invalid runner manifest')
    for key in ('runId', 'cellId', 'mode', 'block', 'workload'):
        require(manifest.get(key) == run[key], 'manifest mismatch: ' + key)
    require(type(manifest.get('block')) is int, 'invalid process block')
    require(type(manifest.get('pid')) is int and manifest['pid'] > 0, 'missing actual PID')
    require(type(manifest.get('processStartedEpochMillis')) is int and manifest['processStartedEpochMillis'] > 0, 'missing OS process creation timestamp')
    require(type(manifest.get('exitCode')) is int and manifest['exitCode'] == 0 and manifest.get('timeout') is False, 'process failed/timed out')
    require(manifest.get('valid') is True and manifest.get('inputsUnchanged') is True, 'runner rejected or changed inputs')
    validate_context(manifest.get('context'), run)
    validate_normalization(manifest, run_root)
    require(isinstance(events, list) and events, 'missing observer events')
    context = manifest['context']
    core = []
    milestones = {}
    generations, completed_generations, extra = {}, set(), []
    last_clocks, active_packs, startup_ready, world_loading = None, None, False, False
    startup_probe = None
    for event in events:
        require(type(event.get('schema')) is int and event['schema'] == 1 and event.get('observerProtocol') == 3,
                'FO requires observer protocol 3 generation-specific input dispatch; old protocols remain unqualified')
        require(event.get('sessionId') == manifest.get('sessionId') and isinstance(manifest.get('sessionId'), str) and manifest['sessionId'], 'mixed/missing observer session')
        require(type(event.get('reloadIndex')) is int and -1 <= event['reloadIndex'] <= 5, 'invalid observer reload index')
        require(event.get('discoveryOnly') is False, 'discovery-only process cannot qualify')
        require(event.get('inputProbeChangedActivity') is False, 'input probe changed activity or activity evidence missing')
        for key in ('overlayPresent','titleFading','menuRendered','worldRendered'):
            require(type(event.get(key)) is bool, 'missing/invalid observer boolean: '+key)
        for key in ('inputProbeAttempts','inputProbeGeneration','menuInputDispatchGeneration','worldInputDispatchGeneration',
                    'renderSequence','inputTickSequence','resourceReloadCount','activeResourceReloads','requestResourceGenerations'):
            require(type(event.get(key)) is int and event[key] >= 0, 'missing/invalid observer counter: '+key)
        kind = event.get('event')
        startup_lifecycle = not startup_ready and kind in ('resource_reload_started','resource_reload_complete')
        require(startup_lifecycle or event.get('windowActive') is True, 'focus lost or unobserved')
        require(event.get('scenario') == run['scenario'], 'observer scenario mismatch')
        for dimension in ('Width', 'Height'):
            require(startup_lifecycle or event.get('framebuffer' + dimension) == context['graphicsSettings'][dimension.lower()], 'framebuffer changed')
        clocks = [event.get(key) for key in ('epochMillis', 'nanoTime', 'jvmUptimeMillis')]
        require(all(type(clock) is int for clock in clocks) and clocks[2] >= 0, 'invalid observer clocks')
        require(last_clocks is None or all(a >= b for a,b in zip(clocks, last_clocks)), 'nonmonotonic observer clocks')
        last_clocks = clocks
        require(kind not in ('error', 'cancelled', 'resource_reload_failed'), 'observer rejected run')
        if run['scenario'] == 'inworld':
            validate_scene_event(event, context['generatedScene'], kind in ('world_ready','frame_ready'))
        if world_loading:
            require((event['inputProbeGeneration'],event['inputProbeAttempts']) == startup_probe,
                    'synthetic menu probe changed during in-world experiment')
        if kind == 'resource_reload_started':
            generation = event.get('resourceGeneration')
            require(type(generation) is int and generation > 0 and generation not in generations, 'invalid/reused resource generation')
            generations[generation] = event
            extra.append(event)
        elif kind == 'resource_reload_complete':
            generation = event.get('resourceGeneration')
            require(generation in generations and generation not in completed_generations, 'failed/unmatched resource generation')
            completed_generations.add(generation)
            extra.append(event)
        elif kind in ('resources_rendered','input_ready','overlay_cleared'):
            generation = event.get('resourceGeneration')
            require(generation in completed_generations and event['activeResourceReloads'] == 0, 'milestone precedes successful resource completion')
            by_kind = milestones.setdefault(generation,{})
            require(kind not in by_kind, 'duplicate generation milestone: '+kind)
            if kind == 'overlay_cleared':
                require(event['overlayPresent'] is False, 'overlay-cleared marker still has overlay')
            else:
                expected_readiness = 'inworld' if world_loading else 'menu'
                validate_readiness(event, expected_readiness, kind == 'input_ready')
                if kind == 'input_ready':
                    require('resources_rendered' in by_kind, 'input acknowledgment precedes rendered milestone')
            by_kind[kind] = event
            extra.append(event)
        else:
            require(kind in ('ready', 'world_loading', 'world_ready', 'reload_requested', 'reload_complete', 'frame_ready', 'complete'), 'unknown observer event')
            core.append(event)
        if kind in ('ready', 'world_ready', 'frame_ready'):
            generation = event.get('resourceGeneration')
            require(type(generation) is int and generation > 0 and event.get('renderedResourceGeneration') == generation, 'new resource generation was not rendered')
            require(generation in completed_generations and event.get('activeResourceReloads') == 0, 'resource completion not proven')
            expected_readiness = 'menu' if kind == 'ready' else run['scenario']
            validate_readiness(event, expected_readiness, True)
            phases = milestones.get(generation,{})
            require('resources_rendered' in phases and 'input_ready' in phases, 'render/input milestones missing before usable frame')
            if kind in ('ready', 'world_ready'):
                resource_completion = next(e for e in extra
                                           if e['event'] == 'resource_reload_complete'
                                           and e['resourceGeneration'] == generation)
                require(event['inputTickSequence'] > resource_completion['inputTickSequence']
                        and event['renderSequence'] > resource_completion['renderSequence'],
                        'old input/render activity claimed readiness')
            packs = event.get('activePackIds')
            require(isinstance(packs, list) and packs and len(packs) == len(set(packs)), 'missing/duplicate resolved resource packs')
            selected = context['orderedSelectedPackIds']
            require([p for p in packs if p in selected] == selected, 'selected pack precedence changed')
            require(active_packs is None or active_packs == packs, 'resolved active pack stack changed')
            active_packs = packs
            if kind == 'ready':
                startup_ready = True
                startup_probe = event['inputProbeGeneration'],event['inputProbeAttempts']
        if kind == 'world_loading': world_loading = True
    expected = [('ready', -1)]
    if run['scenario'] == 'inworld': expected.extend((('world_loading', -1), ('world_ready', -1)))
    for index in range(6): expected.extend((kind,index) for kind in ('reload_requested','reload_complete','frame_ready'))
    expected.append(('complete',5))
    require([(e['event'],e.get('reloadIndex')) for e in core] == expected, 'missing/extra/out-of-order controller events')
    require(set(generations) == completed_generations, 'unfinished resource generations')
    require(core[-1]['overlayPresent'] is False, 'shutdown before overlay retirement')
    startup = core[0]['epochMillis'] - manifest['processStartedEpochMillis']
    require(startup > 0, 'startup must end at interactive menu after actual process creation')
    offset = 3 if run['scenario'] == 'inworld' else 1
    reloads, completions, generation_counts = [], [], []
    rendered_times, input_times, overlay_times, overlay_tails = [], [], [], []
    previous_generation = core[offset - 1]['resourceGeneration']
    previous_frame = core[offset - 1]
    for index in range(6):
        request, complete, frame = core[offset + index*3:offset + index*3+3]
        elapsed = (frame['nanoTime'] - request['nanoTime']) / 1e6
        completion = (complete['nanoTime'] - request['nanoTime']) / 1e6
        require(elapsed >= completion > 0, 'invalid reload elapsed time')
        require(frame['resourceGeneration'] > previous_generation and complete.get('resourceGeneration') == frame['resourceGeneration'], 'old generation/earlier future claimed reload readiness')
        owned = [generation for generation,start in generations.items() if request['nanoTime'] <= start['nanoTime'] <= frame['nanoTime']]
        require(frame['resourceGeneration'] in owned, 'reload frame lacks observed new generation lifecycle')
        require(len(owned) == 1 and frame.get('requestResourceGenerations') == 1,
                'nested/extra generations make this run inconclusive; raw attempts retained')
        resource_completion = next(e for e in extra if e['event'] == 'resource_reload_complete' and e['resourceGeneration'] == frame['resourceGeneration'])
        require(frame['inputTickSequence'] > resource_completion['inputTickSequence']
                and frame['renderSequence'] > resource_completion['renderSequence'], 'old input/render activity claimed readiness')
        phases = milestones.get(frame['resourceGeneration'],{})
        require(set(phases) == {'resources_rendered','input_ready','overlay_cleared'}, 'generation milestones incomplete')
        require(phases['input_ready']['nanoTime'] <= frame['nanoTime'], 'frame readiness precedes acknowledged input')
        old_overlay = milestones.get(previous_generation,{}).get('overlay_cleared')
        require(old_overlay is not None and request['overlayPresent'] is False, 'next reload begins before old overlay retires')
        require(request['nanoTime'] >= max(old_overlay['nanoTime'],previous_frame['nanoTime']) + 1_000_000_000,
                'fixed one-second quiescence after prior usable frame/overlay retirement missing')
        previous_generation = frame['resourceGeneration']
        previous_frame = frame
        generation_counts.append(len(owned))
        if index:
            reloads.append(elapsed)
            completions.append(completion)
            rendered_times.append((phases['resources_rendered']['nanoTime']-request['nanoTime'])/1e6)
            input_times.append((phases['input_ready']['nanoTime']-request['nanoTime'])/1e6)
            overlay_times.append((phases['overlay_cleared']['nanoTime']-request['nanoTime'])/1e6)
            overlay_tails.append(max(0,(phases['overlay_cleared']['nanoTime']-frame['nanoTime'])/1e6))
    return {'startupMs':startup, 'reloadMs':statistics.median(reloads), 'reloadCompleteMs':statistics.median(completions),
            'reloadResourcesRenderedMs':statistics.median(rendered_times), 'reloadInputReadyMs':statistics.median(input_times),
            'reloadOverlayClearedMs':statistics.median(overlay_times),
            'measuredResourcesRenderedMs':rendered_times,'measuredInputReadyMs':input_times,
            'measuredOverlayClearedMs':overlay_times,'measuredOverlayTailMs':overlay_tails,
            'measuredReloadsMs':reloads, 'measuredReloadCompletionsMs':completions, 'activePackIds':active_packs,
            'generationCountsPerReload':generation_counts, 'extraReloadCount':sum(n-1 for n in generation_counts),
            'observerLifecycleEvents':extra}


def validate_headline_input(manifest, run, run_root):
    """Enforce the frozen input contract at the headline analysis boundary."""
    context = manifest.get('context')
    spec = context.get('inputSpec') if isinstance(context, dict) else None
    require(isinstance(spec, dict), 'headline run requires bound inputSpec')
    bindings = spec.get('bindings') if isinstance(spec.get('bindings'), dict) else {
        key: spec.get(key) for key in ('frozenProfileManifest', 'reviewedInputManifest')
    }
    require(all(isinstance(bindings.get(key), dict) for key in ('frozenProfileManifest', 'reviewedInputManifest')),
            'headline inputSpec bindings missing')
    reconstructed = dict(spec)
    reconstructed['frozenProfileManifest'] = bindings['frozenProfileManifest']
    reconstructed['reviewedInputManifest'] = bindings['reviewedInputManifest']
    require(spec.get('mode') == run['mode'] and spec.get('scenario') == run['scenario']
            and spec.get('workload') == run['workload'], 'headline inputSpec differs from scheduled run')
    require(context.get('frozenProfileSha256') == bindings['frozenProfileManifest'].get('sha256'),
            'headline frozen profile lineage mismatch')
    for key in ('mods', 'resourcePacks', 'shaderPacks', 'configs'):
        reconstructed[key] = [dict(item, path='') for item in spec.get(key, [])]
    try:
        input_binding.validate(reconstructed)
    except (ValueError, OSError, KeyError, TypeError, AttributeError) as error:
        raise ValueError('headline input binding rejected: ' + str(error)) from error

    expected = {}
    for item in spec['mods']:
        filename = item.get('filename', item['id'] + '.jar')
        expected['mods/' + filename] = item['sha256'].lower()
    for key, directory in (('configs', 'config'), ('resourcePacks', 'resourcepacks'), ('shaderPacks', 'shaderpacks')):
        for item in spec[key]:
            ident = item['id']
            if key == 'configs' and ident.startswith('config/'):
                ident = ident[7:]
            expected[directory + '/' + ident] = item['sha256'].lower()
    options = spec.get('optionsSnapshot')
    require(isinstance(options, dict) and base.SHA256.fullmatch(options.get('sha256', '')), 'headline options binding missing')
    expected['options.txt'] = options['sha256'].lower()
    root = Path(run_root).resolve()
    actual = {}
    initial = manifest.get('inputHashes')
    require(isinstance(initial, dict), 'headline initial input hashes missing')
    for path, digest in initial.items():
        candidate = Path(path).resolve()
        if candidate.is_relative_to(root) and (candidate.parent == root or candidate.is_relative_to(root / 'mods')
                                               or candidate.is_relative_to(root / 'config')
                                               or candidate.is_relative_to(root / 'resourcepacks')
                                               or candidate.is_relative_to(root / 'shaderpacks')):
            actual[str(candidate.relative_to(root)).replace('\\', '/')] = digest.lower()
    filesystem = {}
    for directory in ('mods', 'config', 'resourcepacks', 'shaderpacks'):
        base_dir = root / directory
        if base_dir.is_dir():
            for candidate in base_dir.rglob('*'):
                if candidate.is_file():
                    filesystem[str(candidate.relative_to(root)).replace('\\', '/')] = digest_file(candidate)
    option_file = root / 'options.txt'
    if option_file.is_file():
        filesystem['options.txt'] = digest_file(option_file)
    require(filesystem.keys() == expected.keys(), 'headline staged files differ from bound input manifest')
    require(filesystem == expected, 'headline staged file hashes differ from bound input manifest')
    require(actual.keys() == expected.keys(), 'headline staged files differ from bound input manifest')
    require(actual == expected, 'headline staged input hashes differ from bound input manifest')
    artifacts = {item.get('id'): item.get('sha256', '').lower() for item in context.get('artifacts', [])}
    require(artifacts == {item['id']: item['sha256'].lower() for item in spec['mods']}, 'headline artifact identity differs from inputSpec')
    configs = {item.get('id'): item.get('sha256', '').lower() for item in context.get('configs', [])}
    expected_configs = {'config/' + item['id'] if not item['id'].startswith('config/') else item['id']: item['sha256'].lower() for item in spec['configs']}
    expected_configs['options.txt'] = options['sha256'].lower()
    require(configs == expected_configs, 'headline config identity differs from inputSpec')


def validate_readiness(event, scenario, require_input):
    generation = event['resourceGeneration']
    require(event['renderedResourceGeneration'] == generation and event['renderSequence'] > 0,
            'old rendered frame used as resource readiness')
    if scenario == 'menu':
        require(event['menuRendered'] is True, 'menu extraction/render not proven')
        if require_input:
            require(event['menuInputDispatchGeneration'] == generation and event['titleFading'] is False,
                    'menu input not acknowledged for current generation or title still fading')
            require(event['inputProbeGeneration'] == generation and event['inputProbeAttempts'] > 0,
                    'menu character probe acknowledgment missing')
    else:
        require(event['worldRendered'] is True, 'world extraction/render not proven')
        if require_input:
            require(event['worldInputDispatchGeneration'] == generation, 'world input not acknowledged for current generation')
    if require_input: require(event['inputTickSequence'] > 0, 'client input loop not observed')


def validate_scene_event(event, scene, require_entities):
    require(event.get('sceneId') == scene['worldId'] and type(event.get('sceneSeed')) is int and event['sceneSeed'] == scene['seed'],
            'runtime scene identity/seed differs from pinned contract')
    require(event.get('sceneImplementationSha256') == scene['implementationSha256'] and event.get('sceneSourceSha256') == scene['sourceSha256'],
            'runtime scene implementation/source differs from pinned contract')
    require(event.get('sceneEntityUuids') == scene['entityUuids'], 'runtime scene expected entity identities changed')
    observed = event.get('sceneObservedEntityUuids')
    require(observed in ([],scene['entityUuids']), 'unexpected runtime scene entity identities')
    if require_entities: require(observed == scene['entityUuids'], 'fixed scene entities not observed before usable world frame')


def validate_normalization(manifest, run_root=None):
    """Runtime-only output differences never replace exact frozen input hashes."""
    if manifest['context'].get('normalizationPolicy') is None:
        require(not manifest.get('normalizationAudit'), 'normalization audit supplied without approved policy')
        return
    require(run_root is not None, 'normalization validation requires the actual run root')
    run_root = Path(run_root).resolve()
    entries = manifest.get('normalizationAudit')
    require(isinstance(entries,list) and len(entries) == len(normalization.RULES)
            and {entry.get('id') for entry in entries} == set(normalization.RULES), 'missing/extra normalization audit paths')
    require(type(manifest.get('rawInputsUnchanged')) is bool, 'missing raw input stability state')
    before,after = manifest.get('inputHashes'),manifest.get('inputHashesAfter')
    require(isinstance(before,dict) and isinstance(after,dict) and set(before) == set(after), 'missing raw input/output hashes')
    approved_changed_paths = set()
    for entry in entries:
        relative = entry['id']
        require(entry.get('rule') == normalization.RULES[relative] and entry.get('accepted') is True, 'unreviewed/rejected metadata output')
        for key in ('beforeSha256','afterSha256','canonicalBeforeSha256','canonicalAfterSha256'):
            require(isinstance(entry.get(key),str) and base.SHA256.fullmatch(entry[key]), 'invalid raw/canonical metadata hash')
        require(entry['canonicalBeforeSha256'] == entry['canonicalAfterSha256'], 'meaningful normalized output change')
        expected_path = (run_root / Path(relative)).resolve()
        matches = [path for path in before if Path(path).resolve() == expected_path]
        require(len(matches) == 1, 'metadata audit does not identify one pinned file')
        path = matches[0]
        require(before[path] == entry['beforeSha256'] and after[path] == entry['afterSha256'], 'audit raw hashes disagree with complete raw manifests')
        changed = before[path] != after[path]
        require(type(entry.get('rawChanged')) is bool and entry['rawChanged'] == changed, 'raw metadata change concealed')
        require(isinstance(entry.get('rawDiff'),str) and (not changed or entry['rawDiff']), 'changed metadata raw diff missing')
        if changed: approved_changed_paths.add(path)
        if relative == 'config/sodium-options.json':
            change = entry.get('notificationPromptChange', {})
            require(type(change.get('before')) is bool and type(change.get('after')) is bool and type(change.get('fingerprintChanged')) is bool, 'missing notification-state audit')
            require(change['before'] == change['after'] or change['before'] is True and change['after'] is False and change['fingerprintChanged'] is True,
                    'unreviewed notification change')
    raw_changes = {path for path in before if before[path] != after[path]}
    require(raw_changes <= approved_changed_paths, 'unreviewed file change hidden by normalization')
    require(manifest['rawInputsUnchanged'] == (not raw_changes), 'raw input stability attestation disagrees with hashes')


def comparable_context(context):
    return {k:v for k,v in context.items() if k not in ('artifacts','configs','configurationRole','qualityContract','inputSpec')}


def analyze(schedule, paths, iterations=10000):
    validate_schedule(schedule)
    expected = {run['runId']:run for run in schedule['runs']}
    records, failures, seen, sessions, process_identities = {}, [], set(), set(), set()
    for path in sorted(map(Path, paths)):
        run_id = None
        try:
            manifest = base.read_json(path)
            run_id = manifest.get('runId')
            require(run_id in expected and run_id not in seen, 'unexpected/duplicate run manifest; rejected attempts cannot be replaced')
            seen.add(run_id)
            require(manifest.get('sessionId') not in sessions, 'session reused across fresh processes')
            sessions.add(manifest.get('sessionId'))
            pid = manifest.get('pid')
            process_started = manifest.get('processStartedEpochMillis')
            require(type(pid) is int and pid > 0 and type(process_started) is int and process_started > 0,
                    'missing OS process identity')
            identity = (pid, process_started)
            require(identity not in process_identities, 'OS process identity reused across fresh processes')
            process_identities.add(identity)
            event_path = Path(manifest.get('eventsFile', ''))
            require(event_path.name == str(event_path) and event_path.name, 'events file must be owned basename')
            events = [base.parse_json(line) for line in (path.parent / event_path).read_text(encoding='utf-8-sig').splitlines() if line.strip()]
            validate_headline_input(manifest, expected[run_id], path.parent)
            records[run_id] = {'context':manifest['context'],
                               'samples':validate_process(manifest, events, expected[run_id], path.parent)}
        except (ValueError, OSError, KeyError, TypeError, AttributeError) as error:
            if isinstance(run_id, str): records.pop(run_id, None)
            failures.append({'path':str(path), 'runId':run_id, 'error':str(error)})
    cells = []
    for cell in schedule['cells']:
        problems, common, mode_contexts, observer = [], None, {}, None
        neutral_artifacts, neutral_configs, normalized_stack = None, None, None
        samples = {mode:[] for mode in cell['processesPerMode']}
        by_mode_artifacts = {}
        for run in sorted((r for r in schedule['runs'] if r['cellId'] == cell['cellId']), key=lambda r:(r['block'],r['mode'])):
            record = records.get(run['runId'])
            if record is None:
                problems.append('missing/invalid process: ' + run['runId'])
                continue
            mode, context = run['mode'], record['context']
            # Default experience is descriptive only and may have different fades;
            # all rendering/pack/JVM/companion inputs must still match.
            current = base.canonical(comparable_context(context))
            own = base.canonical({k:context[k] for k in ('artifacts','configs','qualityContract')})
            artifacts = {i['id']:i['sha256'] for i in context['artifacts']}
            neutral_a = {k:v for k,v in artifacts.items() if k not in ('packforge','quickpack')}
            neutral_c = {i['id']:i['sha256'] for i in context['configs'] if i['id'] not in ('config/packforge.json','config/quick-pack.json')}
            if common is not None and common != current: problems.append('mixed full comparison context: ' + run['runId'])
            if mode in mode_contexts and mode_contexts[mode] != own: problems.append('mode artifact/config changed: ' + run['runId'])
            if neutral_artifacts is not None and neutral_artifacts != neutral_a: problems.append('companion mod/observer mismatch: ' + run['runId'])
            if neutral_configs is not None and neutral_configs != neutral_c: problems.append('companion/shader config mismatch: ' + run['runId'])
            permitted = set().union(*(base.OPTIMIZER_PACK_IDS.get(name,set()) for name in ('packforge','quickpack') if name in artifacts))
            stack = record['samples']['activePackIds']
            selected = context['orderedSelectedPackIds']
            primary_position = min(stack.index(p) for p in selected if p.startswith('file/'))
            if any(stack.index(p) >= primary_position for p in stack if p in permitted): problems.append('optimizer pack overrides selected pack precedence: ' + run['runId'])
            normalized = [p for p in stack if p not in permitted]
            if normalized_stack is not None and normalized_stack != normalized: problems.append('unexplained resolved resource stack difference: ' + run['runId'])
            if mode in ('candidate','quickpack','combined'):
                quality = context.get('qualityContract', {})
                if not isinstance(quality, dict) or quality.get('matched') is not True or not base.SHA256.fullmatch(quality.get('evidenceSha256','')):
                    problems.append('matched quality/fades unavailable or unverified: ' + run['runId'])
            common, neutral_artifacts, neutral_configs, normalized_stack = current, neutral_a, neutral_c, normalized
            mode_contexts[mode] = own
            by_mode_artifacts[mode] = artifacts
            samples[mode].append({'block':run['block'], **record['samples']})
        for left,right in (('saved','configuration'), ('candidate','combined')):
            if left in by_mode_artifacts and right in by_mode_artifacts and by_mode_artifacts[left].get('packforge') != by_mode_artifacts[right].get('packforge'):
                problems.append(f'{left}/{right} must use identical PackForge artifact')
        for right in ('combined','quickpack_default'):
            if 'quickpack' in by_mode_artifacts and right in by_mode_artifacts and by_mode_artifacts['quickpack'].get('quickpack') != by_mode_artifacts[right].get('quickpack'):
                problems.append('Quick Pack binary differs across comparison modes')
        stats = {}
        for mode,values in samples.items():
            stats[mode] = {'validProcesses':len(values), 'expectedProcesses':cell['processesPerMode'][mode], 'role':'gating' if mode in PRIMARY else 'reference'}
            for metric in METRICS:
                if values:
                    numbers = [v[metric] for v in values]
                    stats[mode][metric] = {'medianMs':statistics.median(numbers)}
                    if mode in PRIMARY: stats[mode][metric]['p95Ms'] = base.percentile(numbers,.95)
        comparisons, deltas = {}, {}
        if not problems:
            for mode in ('candidate','combined'):
                salt = int.from_bytes(hashlib.sha256((cell['cellId']+mode).encode()).digest()[:8], 'big')
                comparisons[mode] = noninferiority([x['reloadMs'] for x in samples['quickpack']], [x['reloadMs'] for x in samples[mode]], schedule['seed'] ^ salt, iterations)
            for left,right in (('saved','configuration'), ('configuration','candidate'), ('saved','candidate')):
                deltas[left + '_to_' + right] = {metric:base.comparison([s[metric] for s in samples[left]], [s[metric] for s in samples[right]], schedule['seed'], iterations) for metric in METRICS}
        cells.append({'cellId':cell['cellId'], 'scenario':cell['scenario'], 'problems':problems, 'processSamples':samples,
                      'modeStatistics':stats, 'comparisonsAgainstQuickPack':comparisons, 'configurationAndCodeDeltas':deltas,
                      'passed':not problems and len(comparisons)==2 and all(c['passed'] for c in comparisons.values())})
    return {'schema':1, 'suite':'full-modpack', 'seed':schedule['seed'], 'expectedProcesses':len(expected),
            'receivedManifestCount':len(paths), 'bootstrapIterations':iterations, 'failures':failures, 'cells':cells,
            'filesystemCache':'uncontrolled; fresh process does not imply cold filesystem cache',
            'scope':'FO parity only. Original six-cell startup/reload, companion, correctness and memory gates remain separate.',
            'passed':not failures and all(c['passed'] for c in cells)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    scheduling = sub.add_parser('schedule')
    scheduling.add_argument('--seed', type=int, default=260910)
    scheduling.add_argument('--output', type=Path, required=True)
    analysis = sub.add_parser('analyze')
    analysis.add_argument('--schedule', type=Path, required=True)
    analysis.add_argument('--runs', type=Path, required=True)
    analysis.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    value = make_schedule(args.seed) if args.command == 'schedule' else analyze(base.read_json(args.schedule), list(args.runs.glob('*/manifest.json')))
    with args.output.open('x', encoding='utf-8') as stream: json.dump(value, stream, indent=2, allow_nan=False)
    return 0 if args.command == 'schedule' or value['passed'] else 1


if __name__ == '__main__': sys.exit(main())
