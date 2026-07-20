# PackForge

PackForge is a client-side resource-pack performance and stability mod. The existing **26.1.x-26.2** line remains the stable, full-featured build; exact older Minecraft targets are now produced as core-first beta artifacts.

It is built for players who use large resource packs, modpacks, or heavy visual setups and run into one or both of these problems:

- resource pack reloads taking far too long
- atlas or texture stitching crashes during reload

## Support Matrix

| Minecraft | Java | Pack format | Fabric | Forge | NeoForge | Status |
|---|---:|---:|---|---|---|---|
| 26.1-26.2 | 25 | 84.0-88.0 | Yes | Yes | Yes | Stable, full feature set |
| 1.21.1 | 21 | 34 | Yes | Yes | Yes | `beta.1`, ZIP acceleration core |
| 1.21.4 | 21 | 46 | Yes | Yes | Yes | `beta.1`, ZIP acceleration core |
| 1.21.8 | 21 | 64 | Yes | Yes | Yes | `beta.1`, ZIP acceleration core |
| 1.21.11 | 21 | 75.0 | Yes | Yes | Yes | `beta.1`, ZIP acceleration core |
| 1.20.1 | 17 | 15 | Yes | Yes | No | `beta.1`, ZIP acceleration core |

Compatibility is exact. A 1.21.4 artifact does not claim support for 1.21.2, 1.21.3, or any other untested release.

Server install is not needed. This mod is client-side only.

### Faster Reloads

When Minecraft reloads packs, it can spend a lot of time repeatedly scanning ZIP contents and rebuilding data it has already seen. PackForge reduces that overhead so reloads are more manageable, especially with large packs.

### Atlas Crash Protection

Some packs contain textures that are too large to fit safely into stitched atlases. PackForge can cap and downscale those sprites before they crash the game.

### Optional Retry Recovery

If a texture atlas still fails to stitch, PackForge can retry with more aggressive downscaling. This is off by default because it uses more memory, but it can help with stubborn packs.

## Installation

1. Choose the artifact whose filename contains both your loader and exact Minecraft target
2. Install the target's pinned loader baseline or a later version within the artifact's declared range
3. Put that PackForge jar into your `mods` folder
4. Launch the game once to generate the config file

Fabric uses Loader `0.19.3+`. Fabric API is not required. Mod Menu remains an optional integration on the stable current build.
MixinExtras `0.5.4` is pinned and embedded using each loader's supported nested-jar format; users do not need to install it separately.

Legacy loader baselines:

- Forge: `52.1.15`, `54.1.17`, `58.1.19`, `61.1.9`, and `47.4.21` for the targets in table order
- NeoForge: `21.1.241`, `21.4.157`, `21.8.54`, and `21.11.44`; no 1.20.1 artifact

The 26.x artifacts continue to use their existing baselines:

- Forge `26.1-62.0.9`
- NeoForge `26.1.0.19-beta`

Legacy artifacts include `-beta.1` in both mod metadata and filenames. They advertise only resource indexing, optional pooled ZIP reads, and loader timings. Other saved config values remain in schema v12 but do not activate until an exact-version parity adapter exists; vanilla behavior remains in effect. See [legacy beta notes](docs/legacy-beta.md).

Maturity and version suffixes are stored per platform in the target registry. A Fabric, Forge, or NeoForge artifact can therefore be promoted without changing the maturity of the other loaders for that Minecraft version.

## Config

Config file:

`config/packforge.json`

You can edit it while the game is closed, or change it and reload resources with `F3 + T`.

## Compatibility

Expected to work well with:

- Sodium
- ImmediatelyFast
- FerriteCore

Compatibility combinations are release-gated per exact Minecraft/loader pair; a successful build alone is not treated as runtime proof.

Iris note:

- atlas retry recovery is disabled automatically by default when Iris is present

Not supported:

- OptiFine

## Known Limits

- PackForge does not split the main block atlas
- atlas retry is a fallback tool, not a guarantee
- excluded atlases skip both the cap and retry logic
- very broken packs may still need manual fixes
- legacy beta artifacts do not yet include the 26.x GUI, atlas, model, font, shader, or startup adapters

## Logs And Troubleshooting

If you are testing reload performance:

- enable `loaderTimingsEnabled`
- enable `atlasPhaseTimingsEnabled`

PackForge can write timing information into your logs so you can compare reload behavior before and after changing settings.

If atlas crashes continue:

1. keep `atlasCapEnabled` on
2. lower `atlasCapPx`
3. try enabling `atlasRetryEnabled`

## Building And Verification

Select one exact target through the registry-backed property:

```text
gradlew.bat buildTarget -Ppackforge_target=mc1_21_1 --no-daemon
```

`buildAllSupported` builds all 17 artifacts: the 14 exact legacy beta artifacts plus the three existing 26.x artifacts. Builds fail on duplicate classes/resources and verify filenames, Java class level, mixin compatibility, loader/game ranges, access-widener namespace, generated `pack.mcmeta`, and capability metadata.

The deterministic ZIP tests generate more than 20,000 mixed entries at test time. A non-gating microbenchmark is available with:

```text
gradlew.bat benchmarkPackIndex -Ppackforge_target=mc1_21_1 --no-daemon
```

On Linux, the release benchmark runner loads the packaged mod jar, performs separate disabled/enabled client runs, one priming plus five measured warm reloads, and three fresh-process cold reloads, then enforces the 15% warm improvement, 5% cold regression, and actual `ResourceManager` resolved-resource hash gates:

```text
./scripts/benchmark-client.sh fabric mc1_21_1
```

Pull requests run the full compile/package matrix. The nightly runtime workflow boots each packaged loader/version artifact under a virtual display, enables the fixture, performs repeated `F3+T` reloads, closes the client window, and scans the logs for PackForge and mixin failures. A separate nightly 1.21.1 Fabric job runs the warm/cold/hash benchmark and uploads its CSV, hash, and JSON evidence. Pack add/remove/reorder automation, a packaged 26.2 runtime pass, Windows post-removal handle proof, and compatibility-mod combinations remain promotion gates rather than claims made by the beta artifacts.

## License

See [LICENSE](LICENSE).
