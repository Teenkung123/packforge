"""Deterministic, original procedural resource packs; no input pack is read."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile
import zlib

DEFAULT_OUTPUT = Path(__file__).resolve().parents[2] / '.gradle/performance-rework/workloads'
PARAMETERS = {
    'small': {'seed':260102, 'textures':12, 'models':24, 'parents':4, 'fonts':2},
    'font-heavy': {'seed':260103, 'textures':8, 'models':16, 'parents':4, 'fonts':192},
    'model-heavy': {'seed':260104, 'textures':16, 'models':3000, 'parents':16, 'fonts':2},
    'texture-heavy': {'seed':260105, 'textures':1024, 'models':1024, 'parents':8, 'fonts':2},
}
NAMESPACE = 'packbench'
PREFIX = 'assets/' + NAMESPACE + '/'


def encode(value):
    return (json.dumps(value, sort_keys=True, ensure_ascii=True, separators=(',', ':')) + '\n').encode('utf-8')


def png(width, height, seed, font=False):
    """RGBA8 images with deterministic shapes, color variation, and alpha edges."""
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    pixels = bytearray()
    for y in range(height):
        pixels.append(0)  # PNG filter None; no quantization or lossy transformation.
        for x in range(width):
            value = ((x * 1103515245) ^ (y * 12345) ^ (seed * 2654435761)) & 0xffffffff
            if font:
                # Original synthetic glyph marks, with transparent cell borders.
                edge = min(x % 16, y % 16, 15 - x % 16, 15 - y % 16)
                alpha = 0 if edge < 2 else 128 if edge == 2 else 255
                if ((x % 16) + (y % 16) + seed) % 7 == 0: alpha = 0
            else:
                alpha = (0, 96, 192, 255)[((x // 8) + (y // 8) + seed) % 4]
                if seed % 3 == 0: alpha = 255
            pixels.extend(((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(pixels), level=6)) + chunk(b'IEND', b''))


def texture_shape(index):
    return (64, 128, 256)[index % 3], 4 if index % 16 == 15 else 1


def resources(kind, parameters):
    seed = parameters['seed']
    yield 'pack.mcmeta', encode({'pack':{'description':'Original PackBench ' + kind + ' workload',
                                       'min_format':[84, 0], 'max_format':[88, 0]}})
    # The vanilla block atlas sees this directory in every namespace. No unused
    # arbitrary textures are counted as atlas workload.
    yield 'assets/minecraft/atlases/blocks.json', encode({'sources':[{'type':'minecraft:directory',
                                                                    'source':'bench', 'prefix':'bench/'}]})
    for index in range(parameters['textures']):
        size, frames = texture_shape(index)
        path = PREFIX + f'textures/bench/t{index:04d}.png'
        yield path, png(size, size * frames, seed + index)
        if frames > 1:
            yield path + '.mcmeta', encode({'animation':{'frametime':3, 'interpolate':index % 2 == 1,
                                                        'frames':[0, 1, 2, 3], 'width':size, 'height':size}})
    for index in range(parameters['parents']):
        # Original two-box geometry, with all six textured faces; no vanilla
        # model JSON or texture pixels are copied.
        faces = {side:{'texture':'#surface','uv':[0, 0, 16, 16]} for side in ('north','south','east','west','up','down')}
        yield PREFIX + f'models/bench/parent{index:02d}.json', encode({
            'textures':{'particle':'#surface'}, 'elements':[
                {'from':[0, 0, 0], 'to':[16, 8 + index % 8, 16], 'faces':faces},
                {'from':[2, 8, 2], 'to':[14, 16, 14], 'faces':faces}], 'ambientocclusion':True})
    for index in range(parameters['models']):
        # Groups of eight distinct IDs intentionally have identical *source*
        # while every consumer resolves/bakes its own model and parent chain.
        group = index // 8
        yield PREFIX + f'models/bench/m{index:04d}.json', encode({
            'parent':f'{NAMESPACE}:bench/parent{group % parameters["parents"]:02d}',
            'textures':{'surface':f'{NAMESPACE}:bench/t{group % parameters["textures"]:04d}'}})
        yield PREFIX + f'items/i{index:04d}.json', encode({'model':{
            'type':'minecraft:model', 'model':f'{NAMESPACE}:bench/m{index:04d}'}})
    for index in range(parameters['fonts']):
        side = 512 if kind == 'font-heavy' else 64
        columns = side // 16
        chars = [''.join(chr(0xe000 + row * columns + col) for col in range(columns)) for row in range(columns)]
        yield PREFIX + f'textures/font/f{index:03d}.png', png(side, side, seed + index, font=True)
        yield PREFIX + f'font/f{index:03d}.json', encode({'providers':[
            {'type':'bitmap', 'file':f'{NAMESPACE}:font/f{index:03d}.png', 'height':16, 'ascent':12, 'chars':chars},
            {'type':'reference', 'id':'minecraft:include/space'},
            {'type':'reference', 'id':'minecraft:include/default', 'filter':{'uniform':False}},
            {'type':'reference', 'id':'minecraft:include/unifont'}]})


def generate(kind, output=DEFAULT_OUTPUT, parameters=None):
    parameters = dict(PARAMETERS[kind] if parameters is None else parameters)
    if set(parameters) != {'seed','textures','models','parents','fonts'} or any(type(v) is not int or v < 1 for v in parameters.values()):
        raise ValueError('Positive integer workload parameters required')
    root = Path(output) / kind
    root.mkdir(parents=True, exist_ok=False)  # Never overwrite previous packs/evidence.
    destination = root / ('packbench-' + kind + '.zip')
    inventory = []
    seen = set()
    # Standard deflated pack; compressor version is recorded for reproducibility.
    with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in resources(kind, parameters):
            if name in seen: raise ValueError('Duplicate generated resource')
            seen.add(name)
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
            inventory.append({'path':name, 'bytes':len(data), 'sha256':hashlib.sha256(data).hexdigest()})
    manifest = {'schema':1, 'workload':kind, 'parameters':parameters, 'generatorSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'python':sys.version.split()[0], 'zlib':zlib.ZLIB_RUNTIME_VERSION, 'minecraft':['26.1.2','26.2'],
                'pack':{'id':destination.name, 'path':str(destination.resolve()), 'bytes':destination.stat().st_size,
                        'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()},
                'resourceCount':len(inventory), 'resources':inventory}
    with (root/'manifest.json').open('x',encoding='utf-8') as stream:
        json.dump(manifest,stream,indent=2)
        stream.write('\n')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('workload', choices=[*PARAMETERS, 'all'])
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    for kind in PARAMETERS if args.workload == 'all' else [args.workload]:
        result = generate(kind,args.output)
        print(json.dumps({'workload':kind,'pack':result['pack'],'resources':result['resourceCount']}))
