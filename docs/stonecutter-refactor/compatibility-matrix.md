# Compatibility matrix

Date: 2026-08-13

Outcome labels are `FULL_OPTIMIZED_PATH`, `HOOK_PRESERVING_COALESCED_PATH`, `SAFE_ORIGINAL_PATH`, `EXTERNALLY_OWNED_PATH`, `UNAVAILABLE`, `UNTESTED`, and `FAILED`.

Current branch status is **PARTIAL** and **NOT RELEASE READY**. Registry presence, profile metadata, fixture generation, compilation, packaging, and runtime are separate evidence classes.

## Exact release ledger

The registry contains 22 exact releases and 62 officially applicable loader runtime cells. All current-byte runtime results remain `UNTESTED` until the authoritative direct artifacts are rebuilt and the exact matrix is rerun.

| Minecraft release | Registry loaders | Maturity | Current final-JAR runtime |
|---|---|---|---|
| 1.20.1 | Fabric, Forge | beta | `UNTESTED` |
| 1.20.2 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.20.3 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.20.4 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.20.5 | Fabric | beta | `UNTESTED` |
| 1.20.6 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.1 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.2 | Fabric, NeoForge | beta | `UNTESTED` |
| 1.21.3 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.4 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.5 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.6 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.7 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.8 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.9 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.10 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 1.21.11 | Fabric, Forge, NeoForge | beta | `UNTESTED` |
| 26.1 | Fabric, Forge, NeoForge | stable | `UNTESTED` |
| 26.1.1 | Fabric, Forge, NeoForge | stable | `UNTESTED` |
| 26.1.2 | Fabric, Forge, NeoForge | stable | `UNTESTED` |
| 26.2 | Fabric, Forge, NeoForge | stable | `UNTESTED` |

Forge 1.21.2, Forge/NeoForge 1.20.5, and NeoForge 1.20.1 are absent because the registry records no official cell. This is loader availability, not a compatibility failure.

## Declarative compatibility profiles

`gradle/compatibility-profiles.json` contains 36 structurally validated recipes.

| Profile group | Count | Catalog state | Runtime result |
|---|---:|---|---|
| Quick Pack isolated, Fabric/Forge/NeoForge 1.21.1 | 3 | `AVAILABLE` with pinned URL, version, dependencies, and SHA-256 | `UNTESTED` |
| ImmediatelyFast isolated, Fabric/Forge/NeoForge 1.21.1 | 3 | `AVAILABLE` with pinned URL, version, dependencies, and SHA-256 | `UNTESTED`; path-specific proof currently fails closed |
| Quick Pack + ImmediatelyFast, Fabric 1.21.1 | 1 | `AVAILABLE` with pinned inputs | `UNTESTED` |
| ResourcePackUnbounded, Fabric/Forge/NeoForge 1.21.1 | 3 | `UNAVAILABLE` with dated public-source evidence | `UNAVAILABLE` |
| Remaining declared profiles | 26 | `PENDING_METADATA` | `UNTESTED` |

`AVAILABLE` means inputs can be materialized and hash-verified. It does not mean Minecraft launched or a profile passed. No profile currently has a PASS result.

Quick Pack policy assigns exactly six capabilities to `EXTERNALLY_OWNED_PATH` when detected: resource-pack index, ZIP read pool, font-provider preselection, atlas-mip parallelism, loading-fade control, and loading-status overlay. The other 23 capabilities stay under PackForge policy. This assignment is structurally/unit tested; current final-JAR runtime remains `UNTESTED`.

## Harness and fixture state

- Environment-gated profile reporting emits profile identity, loader/target, loader-observed mods, Quick Pack status, six overlap capabilities, and 23 retained capabilities.
- Schema-2 profile materialization supports hash-addressed caching, safe decoded names, collision protection, SHA-256 verification, dependency staging, fixture metadata, expected markers, and evidence-path transport across all three loader wrappers.
- Nine deterministic 1.21.1 fixtures cover normal, high-entry-count, shader, connected textures, CIT, entity, font-heavy, model-heavy, and mipmap-heavy resources.
- ImmediatelyFast-only path evidence and nonempty configuration overrides fail closed until dedicated instrumentation exists.
- No network download, Minecraft launch, repeated reload, cancellation, semantic hash, or clean-exit profile evidence has been recorded for current bytes.

## Build and publication state

The authoritative graph contains 53 direct loader distributions. Direct-contract AST/self-tests, the direct contract task, and a registry dry-run pass. Nested Gradle remains a temporary parity oracle. Full all-53 compilation/package parity and true preprocessing proof are absent.

Registry publication metadata expects 20 artifacts, but a complete current manifest has not been verified after direct artifact changes. Therefore publication ranges and all 62 exact runtime cells remain unproven for current bytes.

## Historical runtime evidence

Older checkpoints recorded 62/62 exact cells, same-JAR range proof, and a Fabric 1.21.1 Quick Pack 1.5.0 ten-reload result. Those records bind to pre-cutover artifacts. They remain regression references only and do not change current `UNTESTED` results.
