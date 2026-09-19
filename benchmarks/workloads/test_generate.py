import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib

spec = importlib.util.spec_from_file_location('generate', Path(__file__).with_name('generate.py'))
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


class GeneratorTest(unittest.TestCase):
    def test_tiny_pack_deterministic_and_no_overwrite(self):
        parameters = {'seed':7, 'textures':1, 'models':2, 'parents':1, 'fonts':1}
        with tempfile.TemporaryDirectory() as root:
            first = generator.generate('small',Path(root)/'a',parameters)
            second = generator.generate('small',Path(root)/'b',parameters)
            self.assertEqual(first['pack']['sha256'],second['pack']['sha256'])
            self.assertEqual(first['resources'],second['resources'])
            with self.assertRaises(FileExistsError): generator.generate('small',Path(root)/'a',parameters)
            with zipfile.ZipFile(first['pack']['path']) as archive:
                self.assertIsNone(archive.testzip())
                self.assertTrue(all(x.date_time == (2020,1,1,0,0,0) for x in archive.infolist()))
                self.assertTrue(all(x.compress_type == zipfile.ZIP_DEFLATED for x in archive.infolist()))
                self.assertEqual({'max_format':[88,0],'min_format':[84,0], 'description':'Original PackBench small workload'}, json.loads(archive.read('pack.mcmeta'))['pack'])
                model = json.loads(archive.read('assets/packbench/items/i0000.json'))
                self.assertEqual('packbench:bench/m0000',model['model']['model'])
                self.assertEqual(archive.read('assets/packbench/models/bench/m0000.json'),archive.read('assets/packbench/models/bench/m0001.json'))
                atlas=json.loads(archive.read('assets/minecraft/atlases/blocks.json'))
                self.assertEqual('bench',atlas['sources'][0]['source'])

    def test_original_png_dimensions_crc_and_alpha(self):
        data=generator.png(16,16,4)
        self.assertEqual(b'\x89PNG\r\n\x1a\n',data[:8])
        offset=8; chunks={}
        while offset<len(data):
            size=struct.unpack('>I',data[offset:offset+4])[0]
            kind=data[offset+4:offset+8];payload=data[offset+8:offset+8+size]
            crc=struct.unpack('>I',data[offset+8+size:offset+12+size])[0]
            self.assertEqual(zlib.crc32(kind+payload)&0xffffffff,crc)
            chunks[kind]=payload;offset+=size+12
        self.assertEqual((16,16,8,6,0,0,0),struct.unpack('>IIBBBBB',chunks[b'IHDR']))
        raw=zlib.decompress(chunks[b'IDAT']);self.assertEqual(16*65,len(raw))
        alpha={raw[y*65+1+x*4+3] for y in range(16) for x in range(16)}
        self.assertTrue(0 in alpha and 96 in alpha and 192 in alpha)

    def test_font_reference_and_mip_safe_animation_shapes(self):
        # Do not build the 192-font workload in unit tests.
        params={'seed':9,'textures':1,'models':1,'parents':1,'fonts':1}
        resources=dict(generator.resources('small',params))
        font=json.loads(resources['assets/packbench/font/f000.json'])['providers']
        self.assertEqual(['minecraft:include/space','minecraft:include/default','minecraft:include/unifont'],[x['id'] for x in font if x['type']=='reference'])
        self.assertEqual(4,len(font[0]['chars']))
        self.assertEqual(4,len(font[0]['chars'][0]))
        self.assertEqual(192,generator.PARAMETERS['font-heavy']['fonts'])
        self.assertEqual(3000,generator.PARAMETERS['model-heavy']['models'])
        self.assertEqual(1024,generator.PARAMETERS['texture-heavy']['textures'])
        shapes=[generator.texture_shape(index) for index in range(48)]
        self.assertEqual({64,128,256},{size for size,frames in shapes})
        self.assertEqual(3,sum(frames==4 for size,frames in shapes))
        self.assertTrue(all(size%16==0 and size*frames%16==0 for size,frames in shapes))


if __name__=='__main__': unittest.main()
