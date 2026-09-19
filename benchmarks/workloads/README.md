# Original companion workloads

This generator takes no input packs. It never opens, extracts, transforms, or
copies DevelopRP or any other third-party artwork. PNG pixels and model geometry
are original procedural data. Vanilla font providers are referenced by public
resource identifiers; their assets are not copied into these packs.

```powershell
python -m unittest discover -s benchmarks/workloads -p test_generate.py
python benchmarks/workloads/generate.py all
```

Full generation is an explicit offline step, never part of measured client startup.
Default output is `.gradle/performance-rework/workloads/<workload>/`, with a ZIP and
manifest. Existing directories cause failure, preserving earlier artifacts. Each
manifest records fixed seed/counts, generator hash, Python/zlib versions, complete
resource hashes/sizes, final archive hash/size, and supported game versions. ZIP
timestamps and permissions are fixed; PNG/ZIP compression uses zlib level 6.
Repeated generation on the same recorded toolchain produces identical archive
bytes; different zlib versions require new manifests rather than assuming equality.

| Workload | Composition |
|---|---|
| `small` | 12 varied textures, 24 models plus item definitions, 4 parents, 2 bitmap fonts |
| `font-heavy` | 192 unique 512×512 bitmap sheets/fonts, each with 1,024 private-use glyphs and vanilla space/default/Unifont references; 8 atlas textures and 16 models/items |
| `model-heavy` | 3,000 models plus 3,000 item definitions, 16 parent geometries, 16 textures; repeated source under groups of eight distinct model IDs |
| `texture-heavy` | 1,024 textures alternating 64/128/256 square sizes; every 16th is a four-frame vertical sheet; 1,024 models/items, 8 parents |

Font sheets alone represent 192 MiB of uncompressed RGBA pixels. This intentionally
does not constrain the workload to fit PackForge's 128 MiB optimization budget;
that budget must govern extra retained optimization data, not omit required font
providers. Font images have transparent cell borders and partial-alpha edges.
Texture images include opaque, cutout, and partial-alpha content; animated frames
remain powers of two with dimensions divisible by 16, allowing mip level 4.
All workloads use the same geometry/material quality and fixed generator settings
across modes. No benchmark-specific quality reduction is enabled.

## Why these resources are discovered

Source verified against the supplied 26.1 Minecraft decompilation:

- `ModelManager` lists all `models/*.json` resources, including the namespaced
  generated parents and models.
- `ClientItemInfoLoader` lists all `items/*.json`; each generated item definition
  names its own model. Registration of a gameplay item is not required for this
  resource enumeration. Actual runtime parse/bake counts remain a preflight gate.
- `FontManager` lists all font definition stacks, so generated fonts and their
  vanilla references enter preparation even without displaying every custom glyph.
- `DirectoryLister` lists `textures/<source>/*.png` across namespaces. The pack
  contributes a `minecraft:directory` source (`bench`, prefix `bench/`) to the
  vanilla block atlas; every generated workload texture therefore reaches atlas
  loading, including textures not directly referenced by an item model.

The official 26.2 client resource examples confirm the `minecraft:model` item
definition shape, atlas directory source shape, and vanilla font reference IDs.
Official `version.json` records resource pack formats 84.0 for 26.1.2 and 88.0 for
26.2. Generated metadata declares that explicit range using `min_format` and
`max_format`. Actual loader preflights must confirm accepted metadata, resource
counts, no parse/missing-resource warnings, and visual/animation correctness before
these fixtures can support regression claims. Static generation tests alone do
not establish rendering correctness or performance admission.

Use the manifest pack path/hash in a fresh runner spec with the matching workload
ID. Freeze full options after an untimed preflight, including mip level 4, and use
the same ZIP across all comparison modes. Do not combine these packs with DevelopRP
unless explicitly testing a separately labelled combined workload.
