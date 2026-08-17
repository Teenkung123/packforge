# Quick Pack runtime evidence — 2026-08-16

The exact Quick Pack 1.5.0 Fabric pin is intentionally `UNAVAILABLE`: Fabric
Loader 0.17.3 rejects its declared access-widener before PackForge initializes.
That is recorded in the catalog and
`quick-pack-fabric-loader-failure.md`; it is not counted as a PackForge runtime
PASS.

The runnable Forge and NeoForge profiles were executed with the final 1.4
artifacts, ten reloads, stable semantic resource hashes, and clean exits:

| Profile | Result | Ownership/path evidence | Log SHA-256 |
| --- | --- | --- | --- |
| `forge-quick-pack` | PASS, 10 reloads, `cleanExit=true` | `MODULE_HANDOFF`, `EXTERNALLY_OWNED_PATH`, owns `ATLAS_MIP_PARALLEL`, `FONT_PROVIDER_PRESELECTION`, `LOADING_FADE_CONTROL`, `RESOURCE_PACK_INDEX`; retained PackForge capabilities and one resolved-resource index | `6B7375C60CD124980FAF49A25AE262E5FF74B25A22AD5DE36D2A4786D8B6147A` |
| `neoforge-quick-pack` | PASS, 10 reloads, `cleanExit=true` | `MODULE_HANDOFF`, `EXTERNALLY_OWNED_PATH`, same four owned capabilities; retained PackForge capabilities and one resolved-resource index | `C1FEB30A5C4E88C935C3D27D1C9B20E1EDED8827553B1E7DAFFBB89A2DAD8C0C` |

Both PASS lines carry the same resolved-resource SHA-256
`71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4` across
startup and all reloads.  No `ERROR`, `Exception`, or `Critical injection
failure` was present in either log.

The three Fabric Quick Pack companion profiles remain unavailable for the same
immutable loader rejection, and the default-on companion remains unavailable
because no candidate default is promoted.
