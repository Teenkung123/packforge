# Artifact inspection

## Published broken artifact

- File: `packforge-forge-1.3.3-beta.1-mc1.20.1.jar`
- Modrinth version ID: `dPA7zSgm`
- Bytes: 1,317,247
- SHA-256: `040b88f52632f4b67938e615eac11fb22343b2a01b0329f87819f3ebf58b6dce`
- SHA-512: `a6b6bc540437c26bdf3e5f3e8272034110131f521ac2d338fd510c10046accc79e921e2737c1b90404d9c6ea92facefdbadefb5ba5330f3c45256c15b4231f6c`

Inspection results:

- both mixin configs are required and declare `packforge.refmap.json`;
- `observe.SimpleReloadInstanceMixin` and `atlas.SpriteLoaderMixin` are registered;
- final refmap omits mappings for `method_18368` and `method_47660`;
- final mixin class constants retain both raw selectors;
- capability metadata claims reload-listener timings and atlas timings/batching;
- Forge range is `[47.0.0,48)` and Minecraft range is `[1.20.1]`.

## Previous control artifact

- File: `packforge-forge-1.3-beta.1-mc1.20.1.jar`
- Modrinth version ID: `d7wj0W6O`
- Bytes: 1,216,876
- SHA-512: `843374ee79b94e7e9857e1a8cb0ae34ed5115c887c7103492b584d89ef70f956d13fabfb9afecd437264a63b3fd916ded6b182ea472752bc72b43dc6c004b1bf`

## Fixed artifacts

| Loader | File | SHA-256 | Entries | Duplicates |
|---|---|---|---:|---:|
| Fabric | `packforge-fabric-1.3.3-beta.2-mc1.20.1.jar` | `0513e77b6117b414202e02bdce31e9422202ce0a553d22d4777dda95aa0574bc` | 162 | 0 |
| Forge | `packforge-forge-1.3.3-beta.2-mc1.20.1.jar` | `0505099080d0e64b5fa468f2fc0447987eac3951e8fae6d957218d852ae1e85e` | 163 | 0 |

Both artifacts use Java 17 class level and preserve the 1.20.1 capability set. The Forge final refmap contains mappings for `SpriteLoaderMixin` named methods and maps the reload hook from `<init>` plus `StateFactory#create` to the runtime SRG target `m_10863_`. It contains no raw `method_<number>` or `field_<number>` selector.

`verifyExistingArtifacts` inspected the exact 17-file current release set and passed. Runtime acceptance remains tracked separately.

The release and standalone runtime workflows now write and verify a 17-row `SHA256SUMS` manifest over this exact artifact bundle. Fabric/NeoForge packaged smoke consumes the verified bytes from `build/libs` through `PACKFORGE_ARTIFACT_INPUT_DIR`; this proves the bundle identity before matrix smoke. It does not turn Forge rows into final-JAR runtime tests: Forge CI uses source-mode because ForgeGradle userdev targets Mojmap classes while the packaged Forge artifact is SRG-remapped.

Production Forge final-JAR validation uses `scripts/Smoke-Forge-Production.ps1`. The exact beta.2 JAR and SHA-256 above reached the final atlas marker without a fatal Mixin signature on Forge `47.0.0` and `47.4.22`; the existing Modrinth `47.4.20` profile also reached atlas creation. The isolated runs used `ReloadCount=0` and controlled termination. Successful automated F3+T and clean UI-exit evidence are not recorded.
