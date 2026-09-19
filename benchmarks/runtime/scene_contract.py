"""Validate only the neutral observer's freshly generated scene; never read saves."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

WORLD_ID = 'packbench-scene-v1'
SEED = 5782977387033873987
ENTITY_UUIDS = ['00000000-0000-0000-0000-000000005001',
                '00000000-0000-0000-0000-000000005002',
                '00000000-0000-0000-0000-000000005003']
CLASSES = ['dev/packbench/observer/SceneIdentity.class',
           'dev/packbench/observer/BenchmarkScene.class',
           'dev/packbench/observer/BenchmarkScene$SceneCommandOutput.class']
SOURCES = ['src/main/java/dev/packbench/observer/SceneIdentity.java',
           'src/main/java/dev/packbench/observer/BenchmarkScene.java']
MIRRORED = ('generatedBy','mode','worldId','seed','entityUuids','observerSha256','implementationSha256','sourceSha256')
SHA256 = re.compile(r'[a-f0-9]{64}\Z')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_json(data):
    def pairs(items):
        result = {}
        for key,value in items:
            if key in result: raise ValueError('Duplicate scene-manifest key')
            result[key] = value
        return result
    return json.loads(data.decode('utf-8-sig'), object_pairs_hook=pairs)


def validate_identity(scene, observer_sha256):
    if (not isinstance(scene,dict) or set(scene) != {*MIRRORED,'manifestPath','manifestSha256'}
            or scene.get('generatedBy') != 'packbench' or scene.get('mode') != 'observer-created'
            or scene.get('worldId') != WORLD_ID or type(scene.get('seed')) is not int or scene['seed'] != SEED
            or scene.get('entityUuids') != ENTITY_UUIDS):
        raise ValueError('In-world suite requires exact observer-created scene ID, seed and fixed UUIDs; existing saves are forbidden')
    for key in ('observerSha256','implementationSha256','sourceSha256','manifestSha256'):
        if not isinstance(scene.get(key),str) or not SHA256.fullmatch(scene[key]):
            raise ValueError('Scene requires exact SHA-256 identities')
    if scene['observerSha256'] != observer_sha256:
        raise ValueError('Scene observer hash differs from selected observer artifact')
    if not isinstance(scene.get('manifestPath'),str) or not Path(scene['manifestPath']).is_absolute():
        raise ValueError('Scene manifest must have an explicit absolute path')


def implementation_hash(observer):
    result = hashlib.sha256()
    with zipfile.ZipFile(observer) as archive:
        names = archive.namelist()
        for name in CLASSES:
            if names.count(name) != 1: raise ValueError('Expected exactly one neutral scene class: '+name)
            result.update(('/'+name).encode('utf-8'))
            result.update(b'\0')
            result.update(archive.read(name))
    return result.hexdigest()


def validate(scene, observer):
    validate_identity(scene,observer['sha256'])
    path = Path(scene['manifestPath'])
    raw = path.read_bytes()
    if digest(raw) != scene['manifestSha256']: raise ValueError('Scene manifest checksum mismatch')
    manifest = read_json(raw)
    if (not isinstance(manifest,dict) or set(manifest) != {*MIRRORED,'schema','sources'}
            or type(manifest['schema']) is not int or manifest['schema'] != 1):
        raise ValueError('Unreviewed scene manifest schema')
    for key in MIRRORED:
        if manifest[key] != scene[key]: raise ValueError('Scene manifest/spec mismatch: '+key)
    sources = manifest['sources']
    if (not isinstance(sources,list) or len(sources) != len(SOURCES)
            or any(not isinstance(item,dict) or set(item) != {'path','sha256'} for item in sources)
            or [item['path'] for item in sources] != SOURCES
            or any(not isinstance(item['sha256'],str) or not SHA256.fullmatch(item['sha256']) for item in sources)):
        raise ValueError('Unreviewed scene source inventory')
    canonical = json.dumps(sources,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode('utf-8')
    if digest(canonical) != scene['sourceSha256']: raise ValueError('Scene source inventory checksum mismatch')
    observer_path = Path(observer['path'])
    if digest(observer_path.read_bytes()) != scene['observerSha256']: raise ValueError('Scene observer artifact checksum mismatch')
    if implementation_hash(observer_path) != scene['implementationSha256']:
        raise ValueError('Compiled scene implementation checksum mismatch')
    return scene


def arguments(scene):
    return ['-Dpackforge.benchmark.expectedSceneId='+scene['worldId'],
            '-Dpackforge.benchmark.expectedSceneSeed='+str(scene['seed']),
            '-Dpackforge.benchmark.expectedSceneImplementationSha256='+scene['implementationSha256'],
            '-Dpackforge.benchmark.sceneSourceSha256='+scene['sourceSha256']]
