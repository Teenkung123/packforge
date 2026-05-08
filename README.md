# PackForge

Client-only Fabric mod for Minecraft **26.1** that fixes two pain points with large resource packs:

1. **Slow reloads** — vanilla `FilePackResources` re-walks ZIP entries on every `getResource` / `listResources` / `getNamespaces` call. With ~28k-entry packs this turns reloads into multi-minute stalls.
2. **Atlas overflow crashes** — sprites whose stitched area exceeds `GL_MAX_TEXTURE_SIZE` throw `StitcherException` and crash the client.

## Requirements

- Minecraft **26.1**
- Fabric Loader **0.19.2+**
- Fabric API **0.145.1+26.1**
- Java **25**

Client-side only. Safe on servers (mod is `environment: client`).

## Features

### Part I — Loader Optimizer (default ON)

Builds a path-keyed index of each pack ZIP on first access; subsequent lookups skip the full-entry scan. Caches namespaces and prefix-listings off the same index.

- `loaderIndexEnabled` (default `true`) — master switch.
- `loaderTimingsEnabled` (default `false`) — emits `logs/packforge-timings.csv` with per-reload counters.
- `loaderZipPoolEnabled` (default `false`) — opt-in handle pool for parallel reads.

### Part II — Sprite Cap (default ON)

Wraps `SpriteResourceLoader.create` per atlas with a capped loader that downscales any sprite whose frame dimensions exceed `atlasCapPx`. Scale is the largest power of two that divides both image and frame dimensions, preserving animation strip layout. Produces a per-atlas log line on reload:

```
PackForge atlas minecraft:textures/atlas/blocks.png: sprites=1024 downscaled=12
```

- `atlasCapEnabled` (default `true`)
- `atlasCapPx` (default `256`, range 16–8192)
- `atlasExcludeIds` (default `["minecraft:gui"]`) — atlases the cap skips entirely.

### Part III — Stitch Retry (default OFF)

If a stitch still fails, halves every sprite in the failed list (rebuilt from a retained-source cache) and retries with a fresh `Stitcher`. Bounded retries; original `ReportedException` is re-thrown on exhaustion.

- `atlasRetryEnabled` (default `false`)
- `atlasRetryMaxAttempts` (default `2`, range 1–10)
- `forceDisablePartIIIWithIris` (default `true`) — auto-disables when Iris is detected.

Memory cost: while enabled, each capped sprite keeps a clone of its post-cap `NativeImage` until the next reload. Leave off unless an overflow crash is reproducible.

### Safe Reload Performance

PackForge also includes opt-in reload profiling and conservative CPU optimizations:

- async font provider selection plus a linear first-seen glyph selection path;
- `fontBitmapProviderCacheEnabled` (default `false`) — reload-scoped exact bitmap provider cache;
- `atlasPhaseTimingsEnabled` (default `false`) — CSV/log timings for source, decode, stitch, mip, and upload;
- `atlasMipParallelEnabled` (default `false`) — experimental bounded parallel mip generation;
- `modelParseBatchingEnabled` (default `true`) — batch block model JSON parsing to reduce task overhead;
- `modelDuplicateParseCacheEnabled` (default `false`) — reload-scoped exact duplicate block model parse cache.

PackForge does **not** split the `minecraft:blocks` atlas. Block atlas splitting is intentionally out of scope because current chunk rendering binds one terrain atlas and is not page-aware. Atlas cap/downscale/retry remains the supported block-atlas safety path.

### Experimental Atlas Split Guard

The config contains a reserved, default-off guard for future item/particle atlas split experiments. This build does not ship a runtime split renderer yet.

- `experimentalAtlasSplit` (default `false`) — master gate for future experiments.
- `atlasSplitTargets` (default `["minecraft:items", "minecraft:particles"]`) — `minecraft:blocks` is rejected and removed automatically.
- `atlasSplitFallbackToDownscale` (default `true`) — keep cap/downscale fallback active for unsafe cases.
- `atlasSplitDisableWithIris` (default `true`) — shader-stack guard.
- `atlasSplitModelCoherence` (default `true`) — reserved item-model routing guard.

Do not enable block atlas split: PackForge will ignore it. A real split implementation must be item/particle-only first, with baking-time lookup, validator shims, and fallback to downscale.

## Configuration

Config file: `config/packforge.json` (auto-created on first launch). Edit and reload (`F3+T`) — values are read live.

```json
{
  "configVersion": 5,
  "loaderIndexEnabled": true,
  "loaderTimingsEnabled": false,
  "fontBitmapProviderCacheEnabled": false,
  "atlasPhaseTimingsEnabled": false,
  "atlasMipParallelEnabled": false,
  "atlasMipBatchSize": 128,
  "modelParseBatchingEnabled": true,
  "modelParseBatchSize": 64,
  "modelDuplicateParseCacheEnabled": false,
  "atlasCapEnabled": true,
  "atlasCapPx": 256,
  "atlasExcludeIds": ["minecraft:gui"],
  "atlasRetryEnabled": false,
  "atlasRetryMaxAttempts": 2,
  "forceDisablePartIIIWithIris": true,
  "experimentalAtlasSplit": false,
  "atlasSplitTargets": ["minecraft:items", "minecraft:particles"],
  "atlasSplitMaxTiers": 1,
  "atlasSplitFallbackToDownscale": true,
  "atlasSplitDisableWithIris": true,
  "atlasSplitDisableWithSodium": false,
  "atlasSplitModelCoherence": true,
  "atlasSplitDiagnostics": true
}
```

## Build

```bash
./gradlew build
```

Artifact: `build/libs/packforge-*.jar`. Drop into `mods/`.

Dev launch:

```bash
./gradlew runClient
```

## Compatibility

- Sodium / ImmediatelyFast / FerriteCore: expected OK.
- Iris: Part III auto-disabled by default. Parts I and II run normally.
- Atlas split: blocks unsupported; future item/particle experiments are default-off and Iris-guarded.
- OptiFine: not supported (Fabric mod).

## Known limits

- Part III rebuilds sprites from the post-cap clone, not the original source — repeated halves compound from the capped baseline.
- Excluded atlases (`atlasExcludeIds`) skip both the cap and the retry path.
- `atlasCapPx` minimum 16 to keep mip levels sane.
- Block atlas splitting is not planned for this branch; it needs a page-aware terrain renderer, not a small mixin.

## License

See `LICENSE`.
