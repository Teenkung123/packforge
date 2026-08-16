# Compatibility matrix

Date: 2026-08-16

## Superseding 1.4 checkpoint (2026-08-16)

The current 1.4 source/artifact checkpoint includes the full direct/parity build graph, target-specific production fixtures, and the bounded final-JAR matrix recorded at `build/production-matrix/full-62-20260816-batched`. The older Quick Pack ownership statement is historical and is superseded by the version-aware policy: `<1.4` owns resource indexing; `1.4.x` adds loading fade; `1.5+` adds font-provider preselection and atlas-mipmap generation; unknown or failed metadata uses all four known overlaps. ZIP pooling, the PackForge loading-status overlay, diagnostics, toast, atlas protection, sprite decode, and model scheduling remain PackForge-owned.

The current source metrics are 209 production files / 15,814 LOC with a zero-shortfall Phase F metric gate. A clean/incremental build produces exactly 20 current 1.4 artifacts; artifact, nested-JAR, duplicate-entry, and release-manifest verification pass. The full exact runtime matrix also passes 62/62 cells with ten reloads, a positive stable resolved-resource hash, and clean exit. PackIndex/comparative reload benchmarking remains a separate microbenchmark gate; the bounded benchmark attempt timed out before samples.

Outcome labels are `FULL_OPTIMIZED_PATH`, `HOOK_PRESERVING_COALESCED_PATH`, `SAFE_ORIGINAL_PATH`, `EXTERNALLY_OWNED_PATH`, `UNAVAILABLE`, `UNTESTED`, and `FAILED`.

Current branch status is **PARTIAL** and **NOT RELEASE READY**. Current version-1.4 artifacts use the resolved MixinExtras `0.5.4:slim` classifier. Registry presence, profile metadata, fixture generation, compilation, packaging, and runtime are separate evidence classes.

## Exact release ledger

The registry contains 22 exact releases and 62 officially applicable loader runtime cells. Current-byte runtime evidence covers all 62 cells using the authoritative final artifacts; every cell completed ten deterministic reloads, emitted one stable positive resolved-resource SHA-256, and exited cleanly.

| Minecraft release | Registry loaders | Maturity | Current final-JAR runtime |
|---|---|---|---|
| 1.20.1 | Fabric, Forge | beta | `PASS` (ten reloads per loader) |
| 1.20.2 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.20.3 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.20.4 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.20.5 | Fabric | beta | `PASS` (ten reloads) |
| 1.20.6 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.1 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.2 | Fabric, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.3 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.4 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.5 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.6 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.7 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.8 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.9 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.10 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 1.21.11 | Fabric, Forge, NeoForge | beta | `PASS` (ten reloads per loader) |
| 26.1 | Fabric, Forge, NeoForge | stable | `PASS` (ten reloads per loader) |
| 26.1.1 | Fabric, Forge, NeoForge | stable | `PASS` (ten reloads per loader) |
| 26.1.2 | Fabric, Forge, NeoForge | stable | `PASS` (ten reloads per loader) |
| 26.2 | Fabric, Forge, NeoForge | stable | `PASS` (ten reloads per loader) |

Forge 1.21.2, Forge/NeoForge 1.20.5, and NeoForge 1.20.1 are absent because the registry records no official cell. This is loader availability, not a compatibility failure.

## Declarative compatibility profiles

`gradle/compatibility-profiles.json` contains 36 structurally validated recipes.

| Profile group | Count | Catalog state | Runtime result |
|---|---:|---|---|
| Materializable exact-loader recipes | 21 | `AVAILABLE` with pinned URL, version, exact mod IDs/dependencies, loader floors, and SHA-256 | `UNTESTED` |
| Exact-loader profiles with no supported artifact | 11 | `UNAVAILABLE` with dated public-source evidence | `UNAVAILABLE` |
| Remaining declared profiles | 4 | `PENDING_METADATA` | `UNTESTED` |

`AVAILABLE` means inputs can be materialized and hash-verified. It does not mean Minecraft launched or a profile passed. No profile currently has a PASS result.

Twenty unique new artifact pins were independently verified against SHA-256 and embedded loader metadata; exact loader floors, dependencies, and runtime mod IDs are catalogued. Eight formerly pending exact-loader records now carry dated public-source evidence and are explicitly `UNAVAILABLE`; the remaining four stay `PENDING_METADATA` because their exact loader-floor/feature-set disposition is unresolved. Fabric Quick Pack remains pending because its public 1.21.1 artifacts require Fabric Loader `>=0.17.3`, while the exact PackForge 1.21.1 smoke cell uses `0.15.11`. Verification downloads were not retained.

Quick Pack policy assigns exactly four known capabilities to `EXTERNALLY_OWNED_PATH` when detected or when metadata is unknown: resource-pack index, font-provider preselection, atlas-mip parallelism, and loading-fade control. ZIP read pooling and loading-status overlay remain PackForge-owned, as do the other 25 capabilities. This assignment is structurally/unit tested; current final-JAR runtime remains `UNTESTED`.

## Harness and fixture state

- Environment-gated profile reporting emits profile identity, loader/target, loader-observed mods, Quick Pack status, four overlap capabilities, and 25 retained capabilities.
- Schema-2 profile materialization supports hash-addressed caching, safe decoded names, collision protection, SHA-256 verification, dependency staging, fixture metadata, expected markers, and evidence-path transport across all three loader wrappers.
- Nine deterministic 1.21.1 fixtures cover normal, high-entry-count, shader, connected textures, CIT, entity, font-heavy, model-heavy, and mipmap-heavy resources; the generator adds one stable `example`-namespace texture marker to every family so controlled hashing cannot silently pass with zero entries.
- ImmediatelyFast-only path evidence and nonempty configuration overrides fail closed until dedicated instrumentation exists.
- No third-party compatibility-profile download or profile execution has been recorded for current bytes. Three separate focused slim-MixinExtras final-JAR smokes (Fabric `mc1_21_1`, Forge `mc1_20_2`, NeoForge `mc1_21_1`) did launch, perform one reload, emit a positive deterministic resolved-resource hash, initialize MixinExtras 0.5.4, and cleanly exit. The bounded base matrix additionally ran every exact cell for ten reloads with the target-specific production fixture; the nine compatibility fixtures and cancellation/failure scenarios remain separate unexecuted gates.

## Build and publication state

The authoritative graph contains 53 direct loader distributions. Direct-contract AST/self-tests, the direct contract task, a registry dry-run, `buildStonecutterAll`, `verifyStonecutterAll`, and the opt-in all-53 direct/legacy parity oracle pass. Focused Fabric preprocessing proof covers archive capture on 16 targets, SharedZip on 17, ReloadableResourceManager on 18 pre26 targets, RuntimeResourceHash on 17, LoadingOverlayToast on 14, Bitmap provider definition on all 19, and SimpleReload on four targets from 1.21.5 through 1.21.8. General cross-loader preprocessing proof remains bounded to the listed seams; the legacy parity wrapper is retained only as an explicit oracle.

Registry publication metadata expects 20 artifacts. `buildAllSupported` now produces exactly 20 version-1.4 JARs; `verifyAllArtifacts`, `verifyExistingArtifacts`, `inspectArtifactSizes`, and `verifyReleaseManifest` pass. Each final JAR contains exactly one loader-specific slim MixinExtras artifact with no duplicate ZIP entries; the no-rebuild size task reports sizes, nested JARs, largest files, and class/resource totals. The all-62 runtime result binds the current artifact hashes to each exact cell. The legacy wrapper parity path is no longer configured by default; direct leaves are authoritative, with parity retained as an explicit oracle.

## Historical runtime evidence

Older checkpoints recorded 62/62 exact cells, same-JAR range proof, and a Fabric 1.21.1 Quick Pack 1.5.0 ten-reload result. Those records bind to pre-cutover artifacts and remain regression references only. The current 62/62 base result is recorded separately under `full-62-20260816-batched`; compatibility-profile results remain `UNTESTED`.
