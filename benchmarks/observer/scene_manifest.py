#!/usr/bin/env python3
"""Pin the compiled neutral scene and its source; never reads or accepts a save."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent
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


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def implementation_hash(observer):
    digest = hashlib.sha256()
    with zipfile.ZipFile(observer) as archive:
        names = archive.namelist()
        for name in CLASSES:
            if names.count(name) != 1:
                raise ValueError('Expected one neutral observer scene class: ' + name)
            digest.update(('/' + name).encode('utf-8'))
            digest.update(b'\0')
            digest.update(archive.read(name))
    return digest.hexdigest()


def manifest(observer, source_root=ROOT):
    observer = Path(observer)
    sources = [{'path': name, 'sha256': sha256((source_root / name).read_bytes())} for name in SOURCES]
    source_bytes = json.dumps(sources, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('utf-8')
    return {'schema': 1, 'generatedBy': 'packbench', 'mode': 'observer-created',
            'worldId': WORLD_ID, 'seed': SEED, 'entityUuids': ENTITY_UUIDS,
            'observerSha256': sha256(observer.read_bytes()),
            'implementationSha256': implementation_hash(observer),
            'sourceSha256': sha256(source_bytes), 'sources': sources}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--observer', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = manifest(args.observer)
    payload = (json.dumps(result, indent=2, ensure_ascii=True) + '\n').encode('utf-8')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('xb') as stream:
        stream.write(payload)
    spec = {key: result[key] for key in ('generatedBy', 'mode', 'worldId', 'seed',
             'observerSha256', 'implementationSha256', 'sourceSha256', 'entityUuids')}
    spec.update(manifestPath=str(args.output.resolve()), manifestSha256=sha256(payload))
    print(json.dumps({'generatedScene': spec}, indent=2))


if __name__ == '__main__':
    main()
