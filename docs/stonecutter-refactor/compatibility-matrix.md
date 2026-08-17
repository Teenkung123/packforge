# Compatibility matrix

Date: 2026-08-16

## Superseding 1.4 checkpoint (2026-08-16)

The current 1.4 source/artifact checkpoint includes the full direct/parity build graph, target-specific production fixtures, and the bounded final-JAR matrix recorded at `build/production-matrix/full-62-20260816-batched`. The older Quick Pack ownership statement is historical and is superseded by the version-aware policy: `<1.4` owns resource indexing; `1.4.x` adds loading fade; `1.5+` adds font-provider preselection and atlas-mipmap generation; unknown or failed metadata uses all four known overlaps. ZIP pooling, the PackForge loading-status overlay, diagnostics, toast, atlas protection, sprite decode, and model scheduling remain PackForge-owned.

The current generated source metrics are 216 production files / 16,731 LOC; the
Phase F metric gate is `FAIL` with a 915-LOC shortfall and status
`IMPLEMENTED_UNVERIFIED`. A clean/incremental build produces exactly 20 current
1.4 artifacts; artifact, nested-JAR, duplicate-entry, and release-manifest
verification pass. The retained pre-path-marker exact runtime matrix passed
62/62 cells, and the current-byte final run now records 62/62 PASS with ten
reloads per cell. PackIndex/comparative reload benchmarking remains a separate
microbenchmark gate.

Outcome labels are `FULL_OPTIMIZED_PATH`, `HOOK_PRESERVING_COALESCED_PATH`, `SAFE_ORIGINAL_PATH`, `EXTERNALLY_OWNED_PATH`, `UNAVAILABLE`, `UNTESTED`, and `FAILED`.

Current branch status is **PARTIAL** and **NOT RELEASE READY**. Current version-1.4 artifacts use the resolved MixinExtras `0.5.4:slim` classifier. Registry presence, profile metadata, fixture generation, compilation, packaging, and runtime are separate evidence classes.

## Historical exact release ledger (pre-marker checkpoint)

The registry contains 22 exact releases and 62 officially applicable loader
runtime cells. The table below preserves the pre-path-marker 62/62 runtime
checkpoint; it is not current-byte final-distribution proof. The current
20-artifact structural set, manifest, and final-byte 62-cell runtime result are
verified; final evidence is recorded separately.

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
| Materializable exact-loader recipes | 21 | `AVAILABLE` with pinned URL, version, exact mod IDs/dependencies, loader floors, and SHA-256 | 14 `SAFE_ORIGINAL_PATH`, four `HOOK_PRESERVING_COALESCED_PATH`, two `EXTERNALLY_OWNED_PATH`, one documented `FAILED` renderer-environment run |
| Exact-loader profiles with no supported artifact | 15 | `UNAVAILABLE` with dated public-source evidence | `UNAVAILABLE` |
| Remaining declared profiles | 0 | No `PENDING_METADATA` or `UNTESTED` records remain | — |

`AVAILABLE` means inputs can be materialized and hash-verified. The current
available profiles now also have immutable focused runtime evidence or a narrow
documented failure disposition; this does not substitute for the complete
62-cell base matrix.

Pinned artifact inputs are independently SHA-256 and metadata verified; exact
loader floors, dependencies, and runtime mod IDs are catalogued. Fabric Quick
Pack and the other unsupported exact-loader records are explicitly
`UNAVAILABLE` with dated evidence because their loader floors or platform
artifacts cannot be used by the declared cells. Verification downloads are not
retained as source dependencies.

Quick Pack policy assigns exactly four known capabilities to `EXTERNALLY_OWNED_PATH` when detected or when metadata is unknown: resource-pack index, font-provider preselection, atlas-mip parallelism, and loading-fade control. ZIP read pooling and loading-status overlay remain PackForge-owned, as do the other 25 capabilities. This assignment is structurally/unit tested; the focused current final-JAR profile results are recorded in the evidence files above.

## Harness and fixture state

- Environment-gated profile reporting emits profile identity, loader/target, loader-observed mods, Quick Pack status, four overlap capabilities, and 25 retained capabilities.
- Schema-2 profile materialization supports hash-addressed caching, safe decoded names, collision protection, SHA-256 verification, dependency staging, fixture metadata, expected markers, and evidence-path transport across all three loader wrappers.
- Nine deterministic 1.21.1 fixtures cover normal, high-entry-count, shader, connected textures, CIT, entity, font-heavy, model-heavy, and mipmap-heavy resources; the generator adds one stable `example`-namespace texture marker to every family so controlled hashing cannot silently pass with zero entries.
- ImmediatelyFast-only path evidence and nonempty configuration overrides fail closed until dedicated instrumentation exists.
- Current third-party profile execution is recorded under
  `docs/stonecutter-refactor/evidence/`: all available profiles ran ten
  controlled reloads (the VulkanMod renderer crash is the single documented
  failure), emitted stable hashes where startup completed, and retained clean
  exit/fatal-marker evidence. The bounded base matrix remains historical for
  cells affected by the refreshed artifact; heavy/cancellation scenario
  evidence is retained separately.

## Build and publication state

The authoritative graph contains 53 direct loader distributions. Direct-contract AST/self-tests, the direct contract task, a registry dry-run, `buildStonecutterAll`, `verifyStonecutterAll`, and the opt-in all-53 direct/legacy parity oracle pass. Focused Fabric preprocessing proof covers archive capture on 16 targets, SharedZip on 17, ReloadableResourceManager on 18 pre26 targets, RuntimeResourceHash on 17, LoadingOverlayToast on 14, Bitmap provider definition on all 19, and SimpleReload on four targets from 1.21.5 through 1.21.8. General cross-loader preprocessing proof remains bounded to the listed seams; the legacy parity wrapper is retained only as an explicit oracle.

Registry publication metadata expects 20 artifacts. `buildAllSupported` now produces exactly 20 version-1.4 JARs; `verifyAllArtifacts`, `verifyExistingArtifacts`, `inspectArtifactSizes`, and `verifyReleaseManifest` pass. Each final JAR contains exactly one loader-specific slim MixinExtras artifact with no duplicate ZIP entries; the no-rebuild size task reports sizes, nested JARs, largest files, and class/resource totals. The all-62 runtime result binds the current artifact hashes to each exact cell. The legacy wrapper parity path is no longer configured by default; direct leaves are authoritative, with parity retained as an explicit oracle.

## Historical runtime evidence

Older checkpoints recorded 62/62 exact cells, same-JAR range proof, and a Fabric
1.21.1 Quick Pack 1.5.0 ten-reload result. Those records bind to pre-cutover
artifacts and remain regression references only. The current focused profile
summaries are immutable evidence, and the complete current-byte 62-cell base
rerun is now recorded in `evidence/final-validation-2026-08-17.md`.
