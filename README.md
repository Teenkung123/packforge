# PackForge

PackForge is a client-side performance and stability mod for Minecraft **26.1**.

It is built for players who use large resource packs, modpacks, or heavy visual setups and run into one or both of these problems:

- resource pack reloads taking far too long
- atlas or texture stitching crashes during reload

PackForge supports:

- Fabric
- Forge
- NeoForge

Server install is not needed. This mod is client-side only.

### Faster Reloads

When Minecraft reloads packs, it can spend a lot of time repeatedly scanning ZIP contents and rebuilding data it has already seen. PackForge reduces that overhead so reloads are more manageable, especially with large packs.

### Atlas Crash Protection

Some packs contain textures that are too large to fit safely into stitched atlases. PackForge can cap and downscale those sprites before they crash the game.

### Optional Retry Recovery

If a texture atlas still fails to stitch, PackForge can retry with more aggressive downscaling. This is off by default because it uses more memory, but it can help with stubborn packs.

## Installation

1. Use Minecraft **26.1**
2. Install the correct loader for your client
3. Put the matching PackForge jar into your `mods` folder
4. Launch the game once to generate the config file

Fabric also needs:

- Fabric Loader `0.19.2+`
- Fabric API `0.148.0+26.1.2`

Forge build target:

- Forge `26.1-62.0.9`

NeoForge build target:

- NeoForge `26.1.0.19-beta`

Java requirement:

- Java `25`

## Config

Config file:

`config/packforge.json`

You can edit it while the game is closed, or change it and reload resources with `F3 + T`.

## Compatibility

Expected to work well with:

- Sodium
- ImmediatelyFast
- FerriteCore

Iris note:

- atlas retry recovery is disabled automatically by default when Iris is present

Not supported:

- OptiFine

## Known Limits

- PackForge does not split the main block atlas
- atlas retry is a fallback tool, not a guarantee
- excluded atlases skip both the cap and retry logic
- very broken packs may still need manual fixes

## Logs And Troubleshooting

If you are testing reload performance:

- enable `loaderTimingsEnabled`
- enable `atlasPhaseTimingsEnabled`

PackForge can write timing information into your logs so you can compare reload behavior before and after changing settings.

If atlas crashes continue:

1. keep `atlasCapEnabled` on
2. lower `atlasCapPx`
3. try enabling `atlasRetryEnabled`

## License

See [LICENSE](LICENSE).
