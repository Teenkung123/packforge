"""Read-only FO input freezing and reviewable benchmark specification preparation.

Never opens resource/shader/mod archives, launcher accounts, original saves, or
other profile directories. Every generated artifact belongs to the private
benchmark directory. No command launches Minecraft.
"""
import argparse
import copy
import importlib.util
import json
from pathlib import Path
import stat
import sys

MODULE = importlib.util.spec_from_file_location('packbench_runner', Path(__file__).parents[1] / 'runtime/runner.py')
runner = importlib.util.module_from_spec(MODULE)
MODULE.loader.exec_module(runner)
DIRECTORIES = ('mods', 'config', 'resourcepacks', 'shaderpacks')
PROTECTED_NAME = 'DevelopRP-Full-1.9.5.zip'
PROTECTED_SHA256 = '07f8a44fd887bab6cfee1d127f1cd4d1dce6345acbd6c9cd1eed8d4c034a415f'


def ordinary(path):
    """Reject symbolic links and Windows junctions before following a directory."""
    info = path.lstat()
    if stat.S_ISLNK(info.st_mode) or getattr(info, 'st_file_attributes', 0) & 0x400:
        raise ValueError('Profile links/junctions are not eligible inputs: ' + str(path))


def files_beneath(path):
    ordinary(path)
    for child in sorted(path.iterdir()):
        ordinary(child)
        if child.is_dir():
            yield from files_beneath(child)
        elif child.is_file():
            yield child
        else:
            raise ValueError('Unsupported profile file type')


def freeze(profile, destination, packforge_filename, quickpack_filename):
    profile, destination = Path(profile).absolute(), Path(destination).absolute()
    ordinary(profile)
    profile, destination = profile.resolve(), destination.resolve()
    if destination.exists() or destination.is_relative_to(profile) or profile.is_relative_to(destination):
        raise ValueError('Freeze requires a new isolated destination outside original profile')
    if not destination.resolve().is_relative_to(runner.ROOT.resolve()):
        raise ValueError('Profile snapshots must remain in private .gradle/performance-rework')
    for name in (packforge_filename, quickpack_filename):
        if Path(name).name != name: raise ValueError('Optimizer filenames must be reviewed basenames')
    sources = []
    for name in DIRECTORIES:
        folder = profile / name
        if folder.exists(): sources.extend(files_beneath(folder))
    options = profile / 'options.txt'
    ordinary(options)
    sources.append(options)
    before = runner.snapshot(sources)
    protected = profile / 'resourcepacks' / PROTECTED_NAME
    if before.get(str(protected)) != PROTECTED_SHA256:
        raise ValueError('Protected DevelopRP archive does not match approved byte-identical workload')
    active_mods = {p.name for p in sources if p.parent == profile / 'mods' and p.name.endswith('.jar')}
    if packforge_filename not in active_mods:
        raise ValueError('Frozen starting PackForge JAR is missing')
    quick = profile / 'mods' / quickpack_filename
    if quick not in sources: raise ValueError('Reviewed Quick Pack binary is missing')
    selected = json.loads(runner.read_options(options)['resourcePacks'])
    if not isinstance(selected, list) or len(selected) != len(set(selected)):
        raise ValueError('Invalid ordered selected resource packs')
    if 'file/' + PROTECTED_NAME not in selected: raise ValueError('Protected pack is not selected')
    for pack_id in selected:
        if pack_id.startswith('file/'):
            expected = runner.beneath(profile / 'resourcepacks', pack_id[5:])
            if expected not in sources: raise ValueError('Selected directory/dynamic pack requires explicit preparation contract')
    destination.mkdir(parents=True)
    entries = []
    for source in sources:
        relative = source.relative_to(profile).as_posix()
        copied = runner.copy_pinned({'path':str(source), 'sha256':before[str(source)]}, destination / 'inputs' / relative)
        entries.append({'id':relative, 'path':str(copied), 'sha256':before[str(source)], 'bytes':source.stat().st_size})
    if before != runner.snapshot(sources): raise ValueError('Original profile inputs changed while freezing; snapshot rejected')
    manifest = {'schema':1, 'suite':'full-modpack', 'private':True,
                'minecraft':'26.1.2', 'loader':'fabric', 'loaderVersion':'0.19.2',
                'sourceProfile':str(profile), 'files':entries, 'orderedSelectedPackIds':selected,
                'optimizerFiles':{'packforge':'mods/' + packforge_filename, 'quickpack':'mods/' + quickpack_filename},
                'activeTopLevelJarCount':len(active_mods), 'loadedModEntryCount':None,
                'runtimePins':{'jdk':None, 'jvmOptions':None, 'framebuffer':None},
                'pending':['Record actual JDK/JVM arguments and observer-verified fullscreen framebuffer.',
                           'Record authoritative loaded mod list from an isolated preflight.',
                           'Review matched-quality/fade behavior with RRLS and Quick Pack.',
                           'Generate and freeze independent in-world scene.'],
                'handling':'Files copied and hashed as bytes. No archives opened; no accounts, saves, servers, or chat read.'}
    runner.write(destination / 'manifest.json', manifest)
    return manifest


def configuration_control(saved):
    """Old build's effective CPU-mip gate without enabling resize/retry/UV changes.

    This is a benchmark-only reviewed control, not a config migration. Unknown
    fields, visual/fade preferences and every unrelated value remain intact.
    """
    if saved.get('configVersion') != 13:
        raise ValueError('Configuration-only control requires reviewed starting schema 13')
    values = copy.deepcopy(saved)
    changes = {'reloadOptimizerEnabled':True, 'fontPrepareProviderSelectionEnabled':True,
               'loaderIndexEnabled':True, 'fontBitmapProviderCacheEnabled':False,
               'loaderZipPoolEnabled':False, 'resourceReadReuseEnabled':False,
               'largeAtlasFixerEnabled':True, 'atlasMipParallelEnabled':True,
               'atlasCapEnabled':False, 'atlasRetryEnabled':False, 'modelUvTransparencyClampEnabled':False,
               'atlasDecodeBatchingEnabled':False, 'modelAdaptiveBatchingEnabled':False,
               'modelDuplicateParseCacheEnabled':False, 'startupExecutorTuningEnabled':False,
               'startupAsyncDataParsingEnabled':False, 'startupAsyncClassScanEnabled':False,
               'startupAsyncFontAtlasEnabled':False,
               'loaderTimingsEnabled':False, 'reloadListenerTimingsEnabled':False,
               'shaderApplyStallDiagnosticsEnabled':False, 'fontReloadDiagnosticsEnabled':False,
               'atlasPhaseTimingsEnabled':False, 'modelParseTimingEnabled':False, 'startupTimingsEnabled':False}
    preview = [{'setting':key, 'before':values.get(key), 'after':value}
               for key, value in changes.items() if values.get(key) != value]
    values.update(changes)
    return values, preview


def prepare_options(path):
    values = runner.read_options(path)
    # Same benchmark-only throttle changes in every mode. Keep quality, shader,
    # fullscreen, dimensions, selected packs, and all other options unchanged.
    values.update(pauseOnLostFocus='false', inactivityFpsLimit='"minimized"')
    return values


def disable_dynamic_throttling(value):
    result = copy.deepcopy(value)
    result.setdefault('idle', {})['condition'] = 'none'
    states = result.setdefault('states', {})
    for name in ('unfocused', 'invisible', 'unplugged'):
        state = states.setdefault(name, {})
        state.update(frame_rate_target=-1, run_garbage_collector=False)
    return result


def materialize(frozen_path, destination):
    """Prepare shared snapshots and control config; leaves runtime pins explicit."""
    frozen_path, destination = Path(frozen_path).resolve(), Path(destination).resolve()
    if destination.exists() or not destination.is_relative_to(runner.ROOT.resolve()):
        raise ValueError('Preparation requires fresh private destination')
    frozen = runner.read(frozen_path)
    if frozen.get('suite') != 'full-modpack' or frozen.get('private') is not True:
        raise ValueError('Expected private full-modpack freeze')
    for item in frozen['files']: runner.checked(item['path'], item['sha256'], 'sha256')
    by_id = {item['id']:item for item in frozen['files']}
    destination.mkdir(parents=True)
    options = prepare_options(by_id['options.txt']['path'])
    options_path = destination / 'options.txt'
    with options_path.open('x', encoding='utf-8') as stream:
        stream.write(''.join(key + ':' + value + '\n' for key, value in options.items()))
    control, preview = configuration_control(runner.read(by_id['config/packforge.json']['path']))
    runner.write(destination / 'configuration-control.json', control)
    runner.write(destination / 'configuration-control-preview.json', preview)
    dynamic = 'config/dynamic_fps.json'
    if dynamic in by_id:
        runner.write(destination / 'dynamic_fps.json', disable_dynamic_throttling(runner.read(by_id[dynamic]['path'])))
    mods = []
    for item in frozen['files']:
        relative = Path(item['id'])
        if relative.parent.as_posix() == 'mods' and relative.name.endswith('.jar') and item['id'] not in frozen['optimizerFiles'].values():
            mods.append({'id':'fo/' + relative.name, 'filename':relative.name, 'path':item['path'], 'sha256':item['sha256']})
    def inputs(directory):
        return [{'id':i['id'][len(directory)+1:], 'path':i['path'], 'sha256':i['sha256']}
                for i in frozen['files'] if i['id'].startswith(directory + '/')]
    template = {'benchmarkSuite':'full-modpack', 'minecraft':'26.1.2', 'loader':'fabric', 'loaderVersion':'0.19.2',
                'frozenProfileSha256':runner.digest(frozen_path), 'scenario':'menu', 'workload':'fabulously_optimized',
                'framebuffer':{'width':None, 'height':None}, 'java':{'path':None, 'sha256':None}, 'jvmOptions':None,
                'orderedPackIds':frozen['orderedSelectedPackIds'], 'mods':mods,
                'resourcePacks':inputs('resourcepacks'), 'shaderPacks':inputs('shaderpacks'),
                'configs':inputs('config'), 'optionsSnapshot':{'path':str(options_path), 'sha256':runner.digest(options_path)},
                'qualityContract':{'matched':False, 'reason':'Runtime rendering/fade equivalence not verified.'},
                'reloadCount':6, 'timeoutSeconds':900,
                'pack':next(p for p in inputs('resourcepacks') if p['id'] == PROTECTED_NAME)}
    if dynamic in by_id:
        item = next(i for i in template['configs'] if i['id'] == 'dynamic_fps.json')
        item.update(path=str(destination / 'dynamic_fps.json'), sha256=runner.digest(destination / 'dynamic_fps.json'))
    payload_bytes = sum(item['bytes'] for item in frozen['files'])
    runner.write(destination / 'storage-plan.json', {'inputBytesPerProcess':payload_bytes, 'scheduledProcesses':112,
                                                  'copiedInputBytesForSchedule':payload_bytes*112,
                                                  'strategy':'Independent copies from private frozen snapshot; no hardlinks to user inputs.',
                                                  'additionalStorage':'Logs, world saves and diagnostic JFR vary; runner reserves 1 GiB plus 10 GiB free floor per launch.'})
    runner.write(destination / 'base-spec.INCOMPLETE.json', template)
    return {'status':'prepared; runtime pins, per-mode artifacts/configs, observer and quality contract still required',
            'template':str(destination / 'base-spec.INCOMPLETE.json'), 'controlPreview':str(destination / 'configuration-control-preview.json')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    freeze_cmd = commands.add_parser('freeze')
    freeze_cmd.add_argument('--profile', required=True, type=Path)
    freeze_cmd.add_argument('--output', required=True, type=Path)
    freeze_cmd.add_argument('--packforge-file', required=True)
    freeze_cmd.add_argument('--quickpack-file', required=True)
    prepare_cmd = commands.add_parser('prepare')
    prepare_cmd.add_argument('--manifest', required=True, type=Path)
    prepare_cmd.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.command == 'freeze':
        result = freeze(args.profile, args.output, args.packforge_file, args.quickpack_file)
        print(json.dumps({'status':'frozen; no client launched', 'fileCount':len(result['files']), 'pending':result['pending']}, indent=2))
    else: print(json.dumps(materialize(args.manifest, args.output), indent=2))


if __name__ == '__main__':
    main()
