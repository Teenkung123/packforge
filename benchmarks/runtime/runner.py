"""Neutral, offline production-client runner. Python 3.10+, no third-party modules."""
import argparse
import ctypes
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid

NORMALIZER_SPEC = importlib.util.spec_from_file_location('packbench_runtime_normalization', Path(__file__).with_name('normalization.py'))
normalization = importlib.util.module_from_spec(NORMALIZER_SPEC)
NORMALIZER_SPEC.loader.exec_module(normalization)
SCENE_SPEC = importlib.util.spec_from_file_location('packbench_scene_contract', Path(__file__).with_name('scene_contract.py'))
scene_contract = importlib.util.module_from_spec(SCENE_SPEC)
SCENE_SPEC.loader.exec_module(scene_contract)
INPUT_BINDING_SPEC = importlib.util.spec_from_file_location('packbench_input_binding', Path(__file__).with_name('input_binding.py'))
input_binding = importlib.util.module_from_spec(INPUT_BINDING_SPEC)
INPUT_BINDING_SPEC.loader.exec_module(input_binding)

ROOT = Path(__file__).resolve().parents[2] / '.gradle/performance-rework'
JAVA = Path('C:/Program Files/Java/jdk-25/bin/java.exe')
WIDTH = 1280
HEIGHT = 720
CELLS = {'26.1.2': {'fabric': '0.19.3', 'forge': '26.1.2-64.1.0', 'neoforge': '26.1.2.94'},
         '26.2': {'fabric': '0.19.3', 'forge': '26.2-65.1.0', 'neoforge': '26.2.0.48-beta'}}
FULL_MODPACK_MODES = ('vanilla', 'saved', 'configuration', 'candidate', 'quickpack', 'combined', 'quickpack_default')
FEATURES = dict.fromkeys(('is_demo_user', 'has_quick_plays_support', 'is_quick_play_singleplayer',
                         'is_quick_play_multiplayer', 'is_quick_play_realms'), False)
FEATURES['has_custom_resolution'] = True
HOSTS = {'piston-meta.mojang.com', 'piston-data.mojang.com', 'resources.download.minecraft.net',
         'libraries.minecraft.net', 'maven.fabricmc.net', 'meta.fabricmc.net',
         'maven.minecraftforge.net', 'maven.neoforged.net'}


def digest(path, algorithm='sha256'):
    h = hashlib.new(algorithm)
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def write(path, value):
    with Path(path).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def beneath(root, relative):
    root = Path(root).resolve()
    path = (root / relative).resolve()
    if path == root or not path.is_relative_to(root):
        raise ValueError('Path must remain beneath its root: ' + str(relative))
    return path


def rules(items):
    if not items:
        return True
    allowed = False
    for rule in items:
        if set(rule) - {'action', 'os', 'features'} or rule['action'] not in ('allow', 'disallow'):
            raise ValueError('Unsupported launcher rule')
        match = True
        for key, value in rule.get('os', {}).items():
            if key == 'name': match &= value == 'windows'
            elif key == 'arch': match &= value in ('x86_64', 'amd64')
            elif key == 'version': match &= re.search(value, platform.version()) is not None
            else: raise ValueError('Unknown OS rule: ' + key)
        for key, value in rule.get('features', {}).items():
            if key not in FEATURES:
                raise ValueError('Unknown feature rule: ' + key)
            # Modrinth serializes absent optional values as null.
            if value is not None:
                match &= FEATURES[key] == value
        if match:
            allowed = rule['action'] == 'allow'
    return allowed


def arguments(items, replacements):
    result = []
    for item in items:
        values = [item] if isinstance(item, str) else item.get('value', []) if rules(item.get('rules')) else []
        if isinstance(values, str): values = [values]
        for value in values:
            for key, replacement in replacements.items():
                value = value.replace('${' + key + '}', str(replacement))
            if '${' in value:
                raise ValueError('Unresolved launcher argument: ' + value)
            result.append(value)
    return result


def library_path(lib):
    artifact = lib.get('downloads', {}).get('artifact', {})
    if 'path' in artifact: return artifact['path']
    coordinate, _, extension = lib['name'].partition('@')
    parts = coordinate.split(':')
    if len(parts) not in (3, 4): raise ValueError('Invalid Maven coordinate')
    group, name, version = parts[:3]
    suffix = '-' + parts[3] if len(parts) == 4 else ''
    return f'{group.replace(".", "/")}/{name}/{version}/{name}-{version}{suffix}.{extension or "jar"}'


def checked(path, expected=None, algorithm='sha1'):
    path = Path(path).resolve()
    if not path.is_file(): raise ValueError('Missing provisioned file: ' + str(path))
    if expected and digest(path, algorithm) != expected.lower():
        raise ValueError('Checksum mismatch: ' + str(path))
    return path


def version_file(root, identifier, extension):
    if not isinstance(identifier, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]*', identifier):
        raise ValueError('Unsafe version identifier')
    return beneath(root, identifier + '/' + identifier + extension)


def library_key(library):
    coordinate, _, extension = library['name'].partition('@')
    parts = coordinate.split(':')
    if len(parts) not in (3, 4): raise ValueError('Invalid Maven coordinate')
    return parts[0], parts[1], parts[3] if len(parts) == 4 else '', extension or 'jar'


def load_metadata(path, metadata_root=None, active=None):
    """Return merged launcher metadata, provenance files, and selected client JAR.

    Child libraries replace matching parent group/artifact/classifier entries;
    argument arrays concatenate parent then child, without rewriting tokens.
    """
    path = checked(path)
    active = set() if active is None else set(active)
    if path in active: raise ValueError('Version metadata inheritance cycle')
    if len(active) >= 32: raise ValueError('Version metadata inheritance too deep')
    active.add(path)
    child = read(path)
    if metadata_root is not None:
        metadata_root = Path(metadata_root).resolve()
        if not path.is_relative_to(metadata_root): raise ValueError('Metadata escapes version root')
    parents = []
    if 'inheritsFrom' in child:
        if metadata_root is None: raise ValueError('Inherited metadata requires explicit metadataRoot')
        parent, parents, client = load_metadata(version_file(metadata_root, child['inheritsFrom'], '.json'), metadata_root, active)
        merged = {**parent, **child}
        merged['arguments'] = {key: parent.get('arguments', {}).get(key, []) + child.get('arguments', {}).get(key, [])
                               for key in set(parent.get('arguments', {})) | set(child.get('arguments', {}))}
        seen = set()
        merged['libraries'] = []
        for library in child.get('libraries', []) + parent.get('libraries', []):
            key = library_key(library)
            if key not in seen:
                seen.add(key)
                merged['libraries'].append(library)
        # A child's server-only downloads must not erase the inherited client.
        merged['downloads'] = {**parent.get('downloads', {}), **child.get('downloads', {})}
    else:
        merged = dict(child)
        client = path.with_suffix('.jar')
    if 'jar' in child:
        if metadata_root is None: raise ValueError('Explicit jar reference requires metadataRoot')
        client = version_file(metadata_root, child['jar'], '.jar')
        # Referenced JAR may differ from parent: validate against its own metadata.
        jar_meta = version_file(metadata_root, child['jar'], '.json')
        if jar_meta != path:
            jar_description = read(checked(jar_meta)).get('downloads', {}).get('client')
            merged.setdefault('downloads', {}).pop('client', None)
            if jar_description: merged['downloads']['client'] = jar_description
            parents.append(jar_meta)
    if 'client' in child.get('downloads', {}):
        descriptor = child['downloads']['client']
        client = beneath(path.parent, descriptor['path']) if descriptor.get('path') else path.with_suffix('.jar')
    merged.pop('inheritsFrom', None)
    return merged, list(dict.fromkeys([*parents, path])), client


def resolve(spec, game, session):
    """Resolve an installed, flattened official launcher profile; never invokes Gradle."""
    validate_run_options(spec)
    expected_loader = '0.19.2' if full_modpack(spec) else CELLS[spec['minecraft']][spec['loader']]
    if spec['loaderVersion'] != expected_loader:
        raise ValueError('Unpinned Minecraft/loader combination')
    metadata_path = checked(spec['versionJson'])
    meta, metadata_inputs, selected_client = load_metadata(metadata_path, spec.get('metadataRoot'))
    if 'arguments' not in meta: raise ValueError('Legacy argument strings are unsupported')
    libraries = Path(spec['librariesRoot']).resolve()
    inputs = list(metadata_inputs)
    if full_modpack(spec) and spec['scenario'] == 'inworld':
        inputs.append(Path(spec['generatedScene']['manifestPath']).resolve())
    cp = []
    for lib in meta['libraries']:
        if not rules(lib.get('rules')) or lib.get('include_in_classpath') is False: continue
        path = checked(beneath(libraries, library_path(lib)), library_sha1(lib))
        inputs.append(path)
        if path not in cp: cp.append(path)
    client = checked(spec.get('clientJar', selected_client), meta.get('downloads', {}).get('client', {}).get('sha1'))
    cp.append(client)
    inputs.append(client)
    assets = Path(spec['assetsRoot']).resolve()
    index = meta['assetIndex']
    index_path = checked(beneath(assets, 'indexes/' + index['id'] + '.json'), index.get('sha1'))
    inputs.append(index_path)
    for obj in read(index_path)['objects'].values():
        checked(beneath(assets, 'objects/' + obj['hash'][:2] + '/' + obj['hash']), obj['hash'])
    natives = Path(spec['nativesRoot']).resolve()
    native_files = pinned_natives(natives)
    if not native_files: raise ValueError('Provisioned native DLLs are missing')
    inputs.extend(native_files)
    width, height = dimensions(spec)
    tokens = {'auth_player_name': 'PackBench', 'version_name': meta['id'], 'game_directory': game,
              'assets_root': assets, 'assets_index_name': index['id'], 'auth_uuid': '00000000000000000000000000000001',
              'auth_access_token': '0', 'clientid': '0', 'auth_xuid': '0', 'user_type': 'msa',
              'version_type': meta.get('type', 'release'), 'resolution_width': str(width), 'resolution_height': str(height),
              'natives_directory': natives, 'launcher_name': 'neutral-pack-benchmark', 'launcher_version': '1',
              'classpath': ';'.join(map(str, cp)), 'classpath_separator': ';', 'library_directory': libraries}
    jvm = arguments(meta['arguments']['jvm'], tokens)
    if any(x.startswith(('-Xmx', '-Xms', '-javaagent', '-agentlib')) for x in jvm):
        raise ValueError('Metadata overrides controlled JVM configuration')
    if 'logging' in meta and 'client' in meta['logging']:
        logging = meta['logging']['client']
        log_path = checked(spec['loggingConfig'], logging['file']['sha1'])
        inputs.append(log_path)
        jvm += arguments([logging['argument']], {'path': log_path})
    jvm += controlled_jvm_options(spec) + ['-Dpackforge.benchmark.enabled=true',
            '-Dpackforge.benchmark.sessionId=' + session, '-Dpackforge.benchmark.reloads=' + str(spec.get('reloadCount', 6)),
            '-Dpackforge.benchmark.output=' + str(game / 'events.jsonl')]
    jvm += flight_recording_arguments(spec, game)
    jvm += observer_integrity_arguments(spec)
    jvm += correctness_arguments(spec, game)
    command = [str(java_executable(spec)), *jvm, meta['mainClass'], *arguments(meta['arguments']['game'], tokens)]
    if any('gradle' in x.lower() for x in map(str, cp)):
        # Isolated provisioned runtime can live in .gradle/performance-rework; build outputs cannot.
        for path in cp:
            if '.gradle' in str(path).lower() and not path.is_relative_to(ROOT / 'provisioned'):
                raise ValueError('Build/dev classpath rejected: ' + str(path))
    return command, inputs, jvm


def snapshot(paths):
    return {str(path): digest(path) for path in sorted(set(map(Path, paths)))}


def pinned_natives(root):
    # JNA owns these random extraction files and deletes/recreates them at runtime.
    # Stable natives (including jna.dll/jnidispatch.dll) stay in the pinned inputs.
    return sorted(path for path in Path(root).rglob('*.dll')
                  if not re.fullmatch(r'jna[0-9]+\.dll', path.name, flags=re.IGNORECASE))


def library_sha1(library):
    # Fabric's official profile uses top-level hashes, unlike Mojang downloads.
    return library.get('downloads', {}).get('artifact', {}).get('sha1') or library.get('sha1')


def game_options(spec):
    if full_modpack(spec):
        snapshot = spec.get('optionsSnapshot', {})
        values = read_options(checked(snapshot['path'], snapshot['sha256'], 'sha256'))
        required = {'mipmapLevels':'2', 'renderDistance':'8', 'simulationDistance':'6',
                    'graphicsPreset':'"custom"', 'fullscreen':'true'}
        if not options_match(required, values):
            raise ValueError('Full-modpack visual settings differ from frozen FO profile')
        if json.loads(values.get('resourcePacks', 'null')) != spec['orderedPackIds']:
            raise ValueError('Full-modpack ordered selected packs differ from frozen profile')
        return values
    accepted = spec.get('acceptIncompatiblePack', False)
    if type(accepted) is not bool: raise ValueError('acceptIncompatiblePack must be a boolean')
    pack_id = 'file/' + spec['pack']['id']
    return {'resourcePacks': json.dumps(['vanilla', pack_id], separators=(',', ':')),
            'incompatibleResourcePacks': json.dumps([pack_id] if accepted else [], separators=(',', ':')),
            'pauseOnLostFocus': 'false', 'enableVsync': 'false', 'maxFps': '120',
            'graphicsPreset': '"custom"', 'improvedTransparency': 'false', 'inactivityFpsLimit': '"minimized"',
            'mipmapLevels': '4', 'renderDistance': '12', 'simulationDistance': '12', 'onboardAccessibility': 'false'}


def validate_run_options(spec):
    bindings = input_binding.validate(spec)
    count = spec.get('reloadCount', 6)
    if type(count) is not int or not 1 <= count <= 128:
        raise ValueError('reloadCount must be an integer in 1..128')
    for key in ('acceptIncompatiblePack', 'profilingEnabled', 'resourceHashingEnabled', 'flightRecording', 'discoveryOnly'):
        if type(spec.get(key, False)) is not bool:
            raise ValueError(key + ' must be a boolean')
    if spec.get('flightRecording', False) and not spec.get('profilingEnabled', False):
        raise ValueError('flightRecording requires profilingEnabled=true')
    if spec.get('discoveryOnly', False) and not spec.get('profilingEnabled', False):
        raise ValueError('Discovery must be marked non-headline with profilingEnabled=true')
    if spec.get('normalizationPolicy') is not None:
        if not full_modpack(spec) or spec['normalizationPolicy'] != normalization.POLICY:
            raise ValueError('Only reviewed FO runtime metadata normalization is allowed')
    if full_modpack(spec):
        if (spec.get('minecraft'), spec.get('loader'), spec.get('loaderVersion')) != ('26.1.2', 'fabric', '0.19.2'):
            raise ValueError('Full-modpack suite requires frozen FO 26.1.2/Fabric 0.19.2')
        if spec.get('scenario') not in ('menu', 'inworld'):
            raise ValueError('Full-modpack scenario must be menu or inworld')
        if not re.fullmatch(r'[a-f0-9]{64}', spec.get('frozenProfileSha256', '')):
            raise ValueError('Full-modpack suite requires frozen manifest hash')
        dimensions(spec)
        controlled_jvm_options(spec)
        if spec['scenario'] == 'inworld':
            observers = [item for item in spec.get('mods',[]) if item.get('id') == 'observer']
            if len(observers) != 1: raise ValueError('In-world scene requires exactly one pinned observer')
            scene_contract.validate(spec.get('generatedScene'),observers[0])
        elif spec.get('generatedScene') is not None:
            raise ValueError('Menu scenario must not contain scene or world inputs')


def input_spec(spec, bindings):
    """Record the reviewed run identity without retaining payload paths."""
    def identity(items):
        result = []
        for item in items:
            entry = {'id': item['id'], 'sha256': item['sha256'].lower()}
            if 'filename' in item:
                entry['filename'] = item['filename']
            result.append(entry)
        return result
    result = {
        'mode': spec.get('mode'), 'scenario': spec.get('scenario'), 'workload': spec.get('workload'),
        'mods': identity(spec.get('mods', [])), 'resourcePacks': identity(spec.get('resourcePacks', [])),
        'shaderPacks': identity(spec.get('shaderPacks', [])), 'configs': identity(spec.get('configs', [])),
        'optionsSnapshot': ({'path': '',
                            'sha256': spec['optionsSnapshot']['sha256'].lower()}
                           if isinstance(spec.get('optionsSnapshot'), dict) else None),
        'frozenProfileManifest': bindings['frozenProfileManifest'],
        'reviewedInputManifest': bindings['reviewedInputManifest'],
    }
    return result


def full_modpack(spec):
    return spec.get('benchmarkSuite') == 'full-modpack'


def dimensions(spec):
    if not full_modpack(spec): return WIDTH, HEIGHT
    values = spec.get('framebuffer', {})
    if set(values) != {'width', 'height'} or any(type(values[k]) is not int or values[k] <= 0 for k in values):
        raise ValueError('Full-modpack suite requires explicit expected framebuffer dimensions; observer must verify actual dimensions')
    return values['width'], values['height']


def java_executable(spec):
    if not full_modpack(spec): return checked(JAVA)
    item = spec.get('java', {})
    if set(item) != {'path', 'sha256'} or not re.fullmatch(r'[a-f0-9]{64}', item.get('sha256', '')):
        raise ValueError('Full-modpack suite requires exact reviewed JDK executable/hash')
    return checked(item['path'], item['sha256'], 'sha256')


def controlled_jvm_options(spec):
    if not full_modpack(spec): return ['-Xms1G', '-Xmx4G']
    if spec.get('jvmInitialHeap', 'explicit') not in ('explicit', 'default'):
        raise ValueError('Unknown initial-heap policy')
    args = spec.get('jvmOptions', [])
    if (not isinstance(args, list) or not args or any(not isinstance(arg, str) or not arg for arg in args)
            or sum(arg.startswith('-Xms') for arg in args) != (0 if spec.get('jvmInitialHeap') == 'default' else 1)
            or sum(arg.startswith('-Xmx') for arg in args) != 1):
        raise ValueError('Full-modpack JVM options require one Xmx and explicit Xms or reviewed JVM-default initial heap')
    boolean_flags = ('UseG1GC','UseZGC','UseParallelGC','UseSerialGC','UseCompactObjectHeaders','AlwaysPreTouch',
                     'UseStringDeduplication','UnlockExperimentalVMOptions')
    numeric_flags = ('G1NewSizePercent','G1ReservePercent','MaxGCPauseMillis','G1HeapRegionSize')
    def admitted(arg):
        return (re.fullmatch(r'-Xm[sx][0-9]+[kKmMgG]',arg) is not None
                or re.fullmatch(r'-XX:[+-]('+'|'.join(boolean_flags)+')',arg) is not None
                or re.fullmatch(r'-XX:('+'|'.join(numeric_flags)+r')=[0-9]+[kKmMgG]?',arg) is not None)
    if any(not admitted(arg) for arg in args):
        raise ValueError('JVM option is outside reviewed memory/GC controls; arbitrary properties/agents are not admitted')
    return args[:]


def flight_recording_arguments(spec, game):
    validate_run_options(spec)
    if not spec.get('flightRecording', False): return []
    path = beneath(game, 'profile.jfr')
    return ['-XX:StartFlightRecording=settings=profile,dumponexit=true,filename=' + str(path)]


def observer_integrity_arguments(spec):
    width, height = dimensions(spec)
    args = ['-Dpackforge.benchmark.expectedWidth=' + str(width),
            '-Dpackforge.benchmark.expectedHeight=' + str(height),
            '-Dpackforge.benchmark.expectedPackId=file/' + spec['pack']['id']]
    if full_modpack(spec):
        args += ['-Dpackforge.benchmark.scenario=' + spec['scenario'], '-Dpackforge.benchmark.requireGenerationProof=true']
        if spec['scenario'] == 'inworld':
            args += scene_contract.arguments(spec['generatedScene'])
    if spec.get('discoveryOnly', False):
        args.append('-Dpackforge.benchmark.discoveryOnly=true')
    return args


def correctness_arguments(spec, game):
    enabled = spec.get('resourceHashingEnabled', False)
    ids = {item['id'] for item in spec.get('mods', [])}
    if enabled is not ('texture_correctness' in ids):
        raise ValueError('Resource hashing requires both explicit flag and texture_correctness artifact; capture artifacts are forbidden in headline runs')
    if not enabled: return []
    return ['-Dpackforge.correctness.resourceHashingEnabled=true',
            '-Dpackforge.correctness.output=' + str(beneath(game, 'texture-pixels.jsonl'))]


def child_environment(parent):
    """Override only temp paths; never expose or modify the parent environment."""
    temp = str((ROOT.parent / 'modernization-temp').resolve())
    controlled = {'TEMP': temp, 'TMP': temp}
    environment = dict(parent)
    # Windows environment names are case-insensitive even when the supplied map isn't.
    for key in list(environment):
        if key.upper() in controlled:
            del environment[key]
    environment.update(controlled)
    return environment, controlled


def options_match(expected, actual):
    for key, value in expected.items():
        if key in ('resourcePacks', 'incompatibleResourcePacks'):
            try:
                if json.loads(actual.get(key, 'null')) != json.loads(value): return False
            except ValueError: return False
        elif actual.get(key) != value: return False
    return True


def copy_pinned(item, destination):
    source = checked(item['path'], item['sha256'], 'sha256')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open('xb') as output, source.open('rb') as stream:
        shutil.copyfileobj(stream, output)
    checked(destination, item['sha256'], 'sha256')
    return destination


def read_options(path):
    values = {}
    for line in Path(path).read_text(encoding='utf-8-sig').splitlines():
        if not line: continue
        key, separator, value = line.partition(':')
        if not separator or not key or key in values:
            raise ValueError('Malformed or duplicate options key')
        values[key] = value
    return values


def stage_options(spec, destination, expected):
    if 'optionsSnapshot' in spec:
        item = spec['optionsSnapshot']
        if not isinstance(item, dict) or set(item) != {'path', 'sha256'}:
            raise ValueError('optionsSnapshot requires exactly path and sha256')
        if not isinstance(item['sha256'], str) or not re.fullmatch(r'[a-fA-F0-9]{64}', item['sha256']):
            raise ValueError('Invalid optionsSnapshot SHA-256')
        source = checked(item['path'], item['sha256'], 'sha256')
        if not options_match(expected, read_options(source)):
            raise ValueError('optionsSnapshot conflicts with controlled settings')
        copy_pinned(item, destination)
        return [destination]
    with destination.open('x', encoding='utf-8') as output:
        output.write(''.join(f'{key}:{value}\n' for key, value in expected.items()))
    return []


def process_start_millis(process):
    class FileTime(ctypes.Structure):
        _fields_ = [('low', ctypes.c_uint32), ('high', ctypes.c_uint32)]
    values = [FileTime() for _ in range(4)]
    get_times = ctypes.WinDLL('kernel32', use_last_error=True).GetProcessTimes
    get_times.argtypes = [ctypes.c_void_p] + [ctypes.POINTER(FileTime)] * 4
    get_times.restype = ctypes.c_int
    if not get_times(int(process._handle), *(ctypes.byref(value) for value in values)):
        raise ctypes.WinError(ctypes.get_last_error())
    return ((values[0].high << 32 | values[0].low) - 116444736000000000) // 10000


def inspect(spec):
    if 'acceptIncompatiblePack' in spec and type(spec['acceptIncompatiblePack']) is not bool:
        raise ValueError('acceptIncompatiblePack must be a boolean')
    command, inputs, _ = resolve(spec, ROOT / 'runs/INSPECT', 'INSPECT')
    return {'command': command, 'inputHashes': snapshot(inputs), 'status': 'provisioned; not launched'}


def postflight(manifest, game, pinned, controlled, options, normalization_before=None):
    """Preserve failed-run evidence even when an input or event file disappeared."""
    manifest.update(valid=False, inputsUnchanged=False, rawInputsUnchanged=False)
    diagnostics = {'changedInputCount':0, 'changedOwnedInputs':[], 'changedOptionKeys':[],
                   'unexpectedControlledFiles':[], 'missingControlledFiles':[]}
    manifest['postflightDiagnostics'] = diagnostics
    context = manifest.setdefault('context', {})
    context['effectiveOptions'] = {}
    try:
        actual_options = read_options(game / 'options.txt')
        context['effectiveOptions'] = actual_options
        option_hash = {'id':'options.txt', 'sha256':digest(game / 'options.txt')}
        context['configs'] = [item for item in context.get('configs', []) if item['id'] != 'options.txt'] + [option_hash]
        missing = [path for path in pinned if not Path(path).is_file()]
        after = snapshot([path for path in pinned if path not in missing])
        manifest['inputHashesAfter'] = after
        manifest['rawInputsUnchanged'] = manifest['inputHashes'] == after
        changed = [path for path in pinned if path in missing or after.get(str(path)) != manifest['inputHashes'].get(str(path))]
        diagnostics['changedInputCount'] = len(changed)
        diagnostics['changedOwnedInputs'] = [str(Path(path).relative_to(game)) for path in changed if Path(path).is_relative_to(game)]
        if missing: raise FileNotFoundError('Pinned inputs missing after process exit')
        audit, accepted_changes = normalization.audit(normalization_before or {})
        if normalization_before:
            manifest['normalizationAudit'] = audit
            diagnostics['acceptedRuntimeMetadataChanges'] = [entry['id'] for entry in audit if entry['accepted'] and entry['rawChanged']]
        blocking_changes = [path for path in changed if str(Path(path).resolve()) not in accepted_changes]
        diagnostics['changedOptionKeys'] = [key for key in options if not options_match({key:options[key]}, actual_options)]
        actual_controlled = set().union(*(set((game / directory).rglob('*'))
                                         for directory in ('mods', 'config', 'resourcepacks', 'shaderpacks')))
        actual_controlled = {path for path in actual_controlled if path.is_file()}
        diagnostics['unexpectedControlledFiles'] = sorted(str(path.relative_to(game)) for path in actual_controlled - set(controlled))
        diagnostics['missingControlledFiles'] = sorted(str(path.relative_to(game)) for path in set(controlled) - actual_controlled)
        manifest['inputsUnchanged'] = (not blocking_changes and all(entry['accepted'] for entry in audit)
                                      and options_match(options, actual_options) and actual_controlled == set(controlled))
        events = [json.loads(line) for line in (game / 'events.jsonl').read_text().splitlines()]
        manifest['valid'] = bool(manifest['exitCode'] == 0 and not manifest['timeout'] and manifest['inputsUnchanged']
                                 and events and events[-1]['event'] == 'complete'
                                 and all(e['sessionId'] == manifest['sessionId'] and e['event'] != 'error' for e in events))
        if not manifest['valid']:
            manifest['failureReason'] = 'Postflight rejected exit, timeout, input changes, or observer completion'
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        # Exception messages can contain input contents; retain only the error class.
        manifest.update(valid=False, inputsUnchanged=False, failureReason='Postflight failed: ' + type(error).__name__)
    return manifest


def run(spec):
    if os.name != 'nt': raise ValueError('Measured launch requires Windows x64')
    validate_run_options(spec)
    options = game_options(spec)
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH'):
        if os.environ.get(key): raise ValueError('Uncontrolled environment option: ' + key)
    if not re.fullmatch(r'[A-Za-z0-9._-]+', spec['runId']): raise ValueError('Invalid runId')
    mode = spec['mode']
    hardware = spec.get('context', {}).get('hardware', {})
    if (not all(isinstance(hardware.get(k), str) and hardware[k].strip() for k in ('cpu', 'gpu', 'os'))
            or type(hardware.get('ramBytes')) is not int or hardware['ramBytes'] <= 0):
        raise ValueError('Actual CPU, GPU/driver, OS and positive RAM bytes are required')
    if type(spec.get('block')) is not int or not 0 <= spec['block'] < 10:
        raise ValueError('Block must be an integer in 0..9')
    expected_cell = f"{spec['minecraft']}-{spec['loader']}-{spec['workload']}"
    if full_modpack(spec): expected_cell += '-' + spec['scenario']
    if spec['cellId'] != expected_cell:
        raise ValueError('Cell does not match game/loader/workload')
    ids = [item['id'] for item in spec['mods']]
    if len(set(ids)) != len(ids) or ids.count('observer') != 1: raise ValueError('Exactly one observer required')
    validate_mods(spec)
    game = beneath(ROOT / 'runs', spec['runId'])
    if game.exists(): raise ValueError('Run already exists; never overwrite evidence')
    if full_modpack(spec):
        storage_preflight(spec)
    session = str(uuid.uuid4())
    command, inputs, jvm = resolve(spec, game, session)  # Entire preflight before making profile.
    game.mkdir(parents=True)
    controlled = []
    for item in spec['mods']:
        controlled.append(copy_pinned(item, beneath(game / 'mods', item.get('filename', item['id'] + '.jar'))))
    pack = spec['pack']
    packs = spec.get('resourcePacks', [pack]) if full_modpack(spec) else [pack]
    for item in packs:
        controlled.append(copy_pinned(item, beneath(game / 'resourcepacks', item['id'])))
    for item in spec.get('shaderPacks', []) if full_modpack(spec) else []:
        controlled.append(copy_pinned(item, beneath(game / 'shaderpacks', item['id'])))
    configs = []
    for item in spec['configs']:
        path = copy_pinned(item, beneath(game / 'config', item['id']))
        controlled.append(path)
        configs.append({'id': 'config/' + item['id'], 'sha256': digest(path)})
    options_path = game / 'options.txt'
    pinned_options = stage_options(spec, options_path, options)
    configs.append({'id':'options.txt', 'sha256':digest(options_path)})
    # Options may be normalized by vanilla; compare semantic controlled keys after exit.
    java = java_executable(spec)
    before = snapshot(inputs + controlled + pinned_options + [java])
    normalization_before = normalization.capture(game, controlled, spec.get('normalizationPolicy'))
    environment, runtime_environment = child_environment(os.environ)
    Path(runtime_environment['TEMP']).mkdir(parents=True, exist_ok=True)
    context = dict(spec['context'])
    width, height = dimensions(spec)
    selected_packs = [p for pack_id in spec.get('orderedPackIds', []) for p in packs if 'file/' + p['id'] == pack_id] if full_modpack(spec) else packs
    context.update(minecraft=spec['minecraft'], loader=spec['loader'], loaderVersion=spec['loaderVersion'],
                   jvmArgs=[arg for arg in jvm if not arg.startswith(('-Dpackforge.benchmark.sessionId=', '-Dpackforge.benchmark.output='))],
                   graphicsSettings={'width':width, 'height':height, **options}, orderedPacks=[{'id':p['id'], 'sha256':p['sha256'].lower()} for p in selected_packs],
                   artifacts=[{'id':i['id'], 'sha256':i['sha256'].lower()} for i in spec['mods']], configs=configs,
                   filesystemCache='uncontrolled', profilingEnabled=spec.get('profilingEnabled', False),
                   resourceHashingEnabled=spec.get('resourceHashingEnabled', False), runtimeEnvironment=runtime_environment,
                   flightRecording=spec.get('flightRecording', False), optionsSnapshotPinned=bool(pinned_options), effectiveOptions={})
    if full_modpack(spec):
        context.update(benchmarkSuite='full-modpack', scenario=spec['scenario'], frozenProfileSha256=spec['frozenProfileSha256'],
                       orderedSelectedPackIds=spec['orderedPackIds'], shaderPacks=[{'id':p['id'], 'sha256':p['sha256'].lower()} for p in spec['shaderPacks']],
                       qualityContract=spec.get('qualityContract'), configurationRole=spec['mode'],
                       discoveryOnly=spec.get('discoveryOnly', False),
                       normalizationPolicy=spec.get('normalizationPolicy'),
                       generatedScene=(dict(spec['generatedScene']) if spec['scenario'] == 'inworld' else None))
        bindings = input_binding.validate(spec)
        if bindings is not None:
            context['inputSpec'] = input_spec(spec, bindings)
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True, env=environment)
    context['jdk'] = {'version':version.stderr.strip(), 'vendor':'recorded in version output', 'executableSha256':digest(java)}
    manifest = {key:spec[key] for key in ('runId', 'cellId', 'mode', 'block', 'workload')}
    manifest.update(schema=1, sessionId=session, context=context, eventsFile='events.jsonl', inputHashes=before,
                    command=command, timeout=False, valid=False, inputsUnchanged=False)
    with (game / 'stdout.log').open('xb') as stdout, (game / 'stderr.log').open('xb') as stderr:
        process = subprocess.Popen(command, cwd=game, stdout=stdout, stderr=stderr, env=environment)
        try:
            manifest.update(pid=process.pid, processStartedEpochMillis=process_start_millis(process))
            try: manifest['exitCode'] = process.wait(timeout=spec.get('timeoutSeconds', 600))
            except subprocess.TimeoutExpired:
                manifest['timeout'] = True
                process.kill()
                manifest['exitCode'] = process.wait()
            postflight(manifest, game, inputs + controlled + pinned_options + [java], controlled, options, normalization_before)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
            write(game / 'manifest.json', manifest)
    return manifest


def validate_mods(spec):
    ids = [item['id'] for item in spec['mods']]
    if len(set(ids)) != len(ids) or ids.count('observer') != 1:
        raise ValueError('Exactly one observer and unique artifact identities required')
    if spec.get('resourceHashingEnabled', False) is not ('texture_correctness' in ids):
        raise ValueError('Correctness capture artifact/flag must be enabled together and excluded from headline runs')
    filenames = [item.get('filename', item['id'] + '.jar') for item in spec['mods']]
    if (len({name.lower() for name in filenames}) != len(filenames)
            or any(Path(name).name != name or not name.lower().endswith('.jar') for name in filenames)):
        raise ValueError('Mod filenames must be unique JAR basenames')
    mode = spec['mode']
    if full_modpack(spec):
        if mode not in FULL_MODPACK_MODES: raise ValueError('Unknown full-modpack mode')
        expected = ({'packforge'} if mode in ('saved', 'configuration', 'candidate') else
                    {'quickpack'} if mode in ('quickpack', 'quickpack_default') else
                    {'packforge', 'quickpack'} if mode == 'combined' else set())
        if set(ids) & {'packforge', 'quickpack'} != expected:
            raise ValueError('Full-modpack optimizer set does not match mode')
        if not set(ids) - {'observer', 'packforge', 'quickpack'}:
            raise ValueError('Full-modpack mode requires frozen companion mods')
    else:
        expected = 'quickpack' if mode == 'quickpack' else 'packforge'
        if mode == 'vanilla':
            permitted = {'observer','texture_correctness'} if spec.get('resourceHashingEnabled', False) else {'observer'}
            if set(ids) != permitted: raise ValueError('Vanilla comparison must contain only observer and explicitly enabled correctness capture')
        elif mode not in ('current', 'corrected', 'candidate', 'quickpack') or expected not in ids:
            raise ValueError('Comparison optimizer missing')
        if 'packforge' in ids and 'quickpack' in ids:
            raise ValueError('Comparison cannot load both optimizers')


def storage_preflight(spec):
    """Refuse insufficient disk before creating a new run or copying any input."""
    items = [*spec['mods'], *spec['configs'], *spec['resourcePacks'], *spec['shaderPacks'], spec['optionsSnapshot']]
    input_bytes = sum(checked(item['path'], item['sha256'], 'sha256').stat().st_size for item in items)
    # A fresh scene and stdout/JFR can exceed the immutable payload. Reserve one
    # GiB for this run and leave ten GiB for Windows/the user after that reserve.
    required = input_bytes + 11 * 1024**3
    free = shutil.disk_usage(ROOT).free
    if free < required:
        raise ValueError(f'Insufficient private benchmark storage: need {required} bytes including reserve, have {free}')
    return {'inputBytes':input_bytes, 'requiredFreeBytes':required, 'availableFreeBytes':free}


def provision(lock, execute=False):
    """Download a reviewed official URL/hash lock only; never install or run downloaded code."""
    results = []
    for item in lock['files']:
        url = urllib.parse.urlparse(item['url'])
        if url.scheme != 'https' or url.hostname not in HOSTS or url.username or url.password:
            raise ValueError('Non-official download origin')
        algorithm = item.get('algorithm', 'sha1')
        if algorithm not in ('sha1', 'sha256') or not re.fullmatch('[0-9a-fA-F]{' + ('40' if algorithm == 'sha1' else '64') + '}', item['hash']):
            raise ValueError('A published checksum is required')
        target = beneath(ROOT / 'provisioned', item['path'])
        if target.exists(): checked(target, item['hash'], algorithm)
        elif execute:
            target.parent.mkdir(parents=True, exist_ok=True)
            partial = target.with_name(target.name + '.' + uuid.uuid4().hex + '.partial')
            try:
                with urllib.request.urlopen(item['url'], timeout=60) as response, partial.open('xb') as output:
                    if urllib.parse.urlparse(response.url).hostname not in HOSTS: raise ValueError('Non-official redirect')
                    shutil.copyfileobj(response, output)
                checked(partial, item['hash'], algorithm)
                # Exclusive destination avoids replacing files provisioned by another process.
                with target.open('xb') as output, partial.open('rb') as source: shutil.copyfileobj(source, output)
            finally:
                if partial.exists(): partial.unlink()
        results.append({'path':str(target), 'available':target.exists()})
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['inspect', 'run', 'provision'])
    parser.add_argument('spec', type=Path)
    parser.add_argument('--execute', action='store_true', help='Provision downloads; otherwise dry-run')
    args = parser.parse_args()
    spec = read(args.spec)
    result = provision(spec, args.execute) if args.command == 'provision' else inspect(spec) if args.command == 'inspect' else run(spec)
    print(json.dumps(result, indent=2))
    return 1 if args.command == 'run' and not result['valid'] else 0


if __name__ == '__main__':
    try: sys.exit(main())
    except (ValueError, OSError, KeyError) as error:
        print(type(error).__name__ + ': ' + str(error), file=sys.stderr)
        sys.exit(2)
