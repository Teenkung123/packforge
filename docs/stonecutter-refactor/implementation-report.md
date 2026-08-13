# Implementation report

Date: 2026-08-13

Current status: **PARTIAL** on `codex/packforge-stonecutter-refactor`; **NOT RELEASE READY**.

This report describes current implementation state. Earlier full-matrix and Quick Pack runs are preserved below as historical evidence, but later direct-build and artifact changes invalidate them as current final-JAR proof. See `final-validation.md` for current acceptance gaps.

## Current phase status

| Phase | Status | Implemented | Still required |
|---|---|---|---|
| A — audit and source gates | `VERIFIED_COMPLETE` | Deterministic source inventory, duplicate checks, ownership/category metrics, conditional counts, renderer-family counts, and registry/publication counts. | Rerun after later structural changes. |
| B — Quick Pack ownership | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | Exactly six overlap capabilities are handed off; 23 capabilities remain PackForge-owned; mixed non-overlap behavior is retained; UI reports configured/effective ownership. | Current final-JAR profiles on required loaders. |
| C — optional-mod detection | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | Loader-neutral runtime detection and environment-gated profile reporter exist; detection failures fail conservatively. | Current Forge/NeoForge profile launches and retained evidence. |
| D — registry graph | `STRUCTURAL_VERIFIED` | Registry generates 12 source families, 19 registry build targets (direct source nodes), 53 loader distributions, and a separate 62-cell exact runtime ledger. | Keep registry/direct-node bijection mandatory as graph evolves. |
| E — authoritative direct build | `PARTIAL` | All 53 loader distributions are authoritative direct cells; public graph is direct-only; contract validation is mandatory. Fabric native preprocessing is proven for archive capture on 16 targets, SharedZip on 17, ReloadableResourceManager on 18 pre26 targets, and Bitmap provider definition on all 19 targets. | Extend preprocessing beyond bounded pilots; all-53 compile/package parity, Java 17/21/25 proof, remap/refmap/JarJar proof, runtime, then removal of nested parity oracle. |
| F — source ownership | `PARTIAL` | Zero exact/normalized duplicate groups; 15 target/version conditionals; registry-backed source policy; deterministic metric snapshot. | Raw LOC reduction target is not met; retain justification and prevent branch/duplicate regression. |
| G — configuration renderers | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | Unknown fields survive save; integer validation is shared; renderer families reduced to three bodies plus two adapters; atlas retry configured/effective state is separated. | Live UI parity across renderer families and shader/atlas runtime behavior. |
| H — range artifacts | `IMPLEMENTED_UNVERIFIED` | Registry expresses seven publication anchors and 20 loader artifacts without crossing maturity boundaries. | Rebuild current bytes and repeat same-JAR proof for every exact release in each range. |
| I — compatibility profiles | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | 36-profile catalog, pinned available inputs, unavailability evidence, profile reporter, schema-2 materializer, three-loader transport, and deterministic fixtures. | Downloads/launches and current final-JAR PASS evidence. |
| J — default-off evaluation | `VERIFIED_COMPLETE_CONSERVATIVE` | Eleven candidates frozen: five `SAFE_KEEP_DEFAULT_OFF`, six `FAILED_WITH_REASON`, zero promoted. | Runtime/performance gates only if a future promotion is proposed. |
| K — exact final-artifact matrix | `PARTIAL` | Existing-manifest verifier, exact-cell controller, provenance transport, and nine deterministic fixtures exist. | Current 20-JAR manifest plus full 62-cell runtime/reload/fixture/exit proof. |
| L — final reconciliation | `PARTIAL` | Current-status docs no longer present historical 62-cell, Quick Pack, manifest, or hashes as current proof. | Final evidence and rollback checkpoint after H/K and required profiles pass. |

## Current build architecture

`gradle/minecraft-targets.json` remains schema v2 and records 22 exact Minecraft releases, 12 source families, and 19 registry build targets (direct source nodes). Registry expansion creates 53 loader-specific direct distributions. The 62-cell ledger is a runtime acceptance matrix, not a build-node count.

Direct loader leaves are authoritative and public aggregate tasks are direct-only. `validateStonecutterDirectContract` rejects delegation in public leaves and checks registry metadata. Native Stonecutter preprocessing is focused compile- and source-archive-proven for Fabric archive capture across 16 targets, SharedZip across 17, ReloadableResourceManager across 18 pre26 targets, and Bitmap provider definition across all 19; it is not yet the general source transport. Nested Gradle still exists as a temporary parity rollback oracle; broader preprocessing ownership and full all-cell parity remain open.

## Current source and configuration evidence

Latest deterministic snapshot:

```text
productionFiles=214
productionLoc=16471
bridgeFiles=107
bridgeLoc=5451
bridgePercent=33.09
exactDuplicateGroups=0
normalizedDuplicateGroups=0
targetVersionConditionalLines=15
stonecutterConditionalBlocks=6
maxStonecutterConditionalBlockLines=40
rendererBodies=3
rendererAdapters=2
publicationArtifacts=20
```

These metrics prove checked-in source shape only. They do not prove all target compilation or current artifact runtime.

Configuration persistence preserves unknown JSON fields. Shared renderer/model code centralizes categories, option state, validation, configured/effective values, ownership, and save behavior. Atlas retry remains default-off and its configured value is preserved; shader presence guards effective availability instead of mutating saved configuration. Focused tests passed, but no live renderer-family or shader profile was run.

## Current compatibility harness

The compatibility catalog contains 36 recipes:

- Twenty-one `AVAILABLE`/`UNTESTED` recipes with exact loader-family artifacts, dependency sets, runtime IDs, versions, URLs, and SHA-256 pins.
- Three ResourcePackUnbounded 1.21.1 recipes marked `UNAVAILABLE` with dated public-source evidence for Fabric, Forge, and NeoForge.
- Twelve recipes remain `PENDING_METADATA`/`UNTESTED`. Fabric Quick Pack profiles are among them because public 1.21.1 builds require Fabric Loader `>=0.17.3`, while the exact PackForge cell uses `0.15.11`.

Every available recipe is pinned by URL/version/SHA-256 and requires runtime evidence markers. Twenty unique newly added pins were independently checked against their SHA-256 and embedded loader metadata; no downloaded artifacts were retained. `CompatibilityProfileReporter` reports profile ID, loader, target, observed mods, Quick Pack state, six handed-off capabilities, and 23 retained capabilities only when profile environment variables are present.

Schema-2 materialization validates the catalog before resolving inputs, uses a hash-addressed cache, derives safe decoded basenames, verifies SHA-256, transports fixture and expected-marker metadata, and rejects unsupported paths. Offline materialization and AST tests pass. Exactly five safe boolean overrides are merged against a complete 50-field v12 baseline; unsupported keys and values fail closed. No network request, dependency download, Gradle build, or Minecraft launch belongs to this evidence. ImmediatelyFast-only path proof remains unavailable.

Nine deterministic 1.21.1 fixtures cover normal, high-entry-count, shader, connected-textures, CIT, entity, font-heavy, model-heavy, and mipmap-heavy packs. Manifest contracts also cover overlay/namespace/duplicate and malformed-but-ZIP-readable entries. Fixture generation is deterministic; runtime, repeated reload, and cancellation evidence remain open.

## Current default-off decisions

No production default changed. Candidate catalog contains exactly 11 entries:

- Five `SAFE_KEEP_DEFAULT_OFF`: ZIP read pool, font bitmap cache, sprite decode, startup executor, and atlas retry.
- Six `FAILED_WITH_REASON`: atlas mip parallelism is `NOT_STARTED`; adaptive/duplicate model work and three startup async paths are `PARTIAL`.
- Zero promoted candidates.

All six required runtime/performance evidence classes remain `NOT_RUN`. `SAFE_KEEP_DEFAULT_OFF` means conservative non-promotion, not successful benchmark or compatibility proof.

## Current release boundary

Registry metadata still expects 20 artifacts and 62 exact runtime cells. Existing-manifest verification is implemented and mutation-tested, but no complete current 20-JAR manifest has been verified since direct artifact changes. No current same-binary range, full exact matrix, or release-ready hash list is claimed.

## Historical implementation evidence

Historical records remain useful for regressions and design intent:

- Early focused exact-target builds established source-family feasibility across required loader/version boundaries.
- Checkpoint `fbd7b3208347230ae17e90f822bde2e100e82992` recorded same-JAR 1.20.2-1.20.4 runtime proof.
- Checkpoint `5ed1480540162547b3de475e0e9a51e7965d968c` recorded same-JAR 1.20.5-1.21.1 proof and a real Fabric Quick Pack 1.5.0 ten-reload run.
- Checkpoint `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` recorded 62/62 exact cells and 20 publication artifacts.
- Checkpoint `a3402866b217ac159d6a3cec70d3585028f79732` recorded source consolidation, 18/20 artifact hash continuity, four affected Forge reruns, and a final Quick Pack rerun.

Those results bind to older artifact bytes. They must not be used as current release claims after the direct-build cutover and later source/configuration changes.

## Definition of done

Implementation is complete only after all 53 direct distributions pass required build/package checks, current 20 artifacts pass manifest verification, all 62 exact cells pass current final-JAR runtime acceptance, required available profiles execute with retained evidence, same-binary range proof is current, and a new final checkpoint records exact commands, hashes, limitations, and rollback order.
