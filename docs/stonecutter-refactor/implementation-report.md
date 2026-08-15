# Implementation report

Date: 2026-08-15

## Superseding 1.4 checkpoint (2026-08-16)

Use this section for current status; later tables preserve historical checkpoint wording. HEAD is `8aa6844` on branch `1.4`.

- Capability metadata now excludes unreachable atlas-mip, model adaptive/cache/timing, and startup-async scaffolding. The configuration fields remain readable for backward compatibility but are marked unavailable.
- Quick Pack policy is version-aware and no longer claims ZIP pooling or the PackForge status overlay as externally supplied. The Fabric profile-only Loader `0.17.3` override is structural only.
- Current source metrics: 209 production files, 15,814 LOC, 3,370 ledger-adjusted reduction / 3,368 required, zero shortfall, and `phaseFMetricGate=PASS`; Phase F remains `IMPLEMENTED_UNVERIFIED` pending behavior parity.
- Artifact hardening commit `6591e79` verifies one exact loader-specific `mixinextras-*-0.5.4-slim.jar`, rejects normal/alternate top-level copies, recursively checks Forge's `MixinExtras-0.5.4.jar` metadata and required classes, and gates the 128x128, <=32KiB icon. Representative Fabric/Forge/NeoForge target verification passes.
- The root directory still contains 17 stale artifacts from an earlier build; all-20 current rebuild, manifest proof, same-binary ranges, runtime profiles, and full matrix remain open. The PackIndex benchmark attempt timed out and is not evidence.

Current status: **PARTIAL** on `1.4`; **NOT RELEASE READY**. Current loader artifacts use MixinExtras `0.5.4:slim`.

This report describes current implementation state. Earlier full-matrix and Quick Pack runs are preserved below as historical evidence, but later direct-build and artifact changes invalidate them as current final-JAR proof. See `final-validation.md` for current acceptance gaps.

## Current phase status

| Phase | Status | Implemented | Still required |
|---|---|---|---|
| A — audit and source gates | `IMPLEMENTED_UNVERIFIED` | Deterministic source inventory, duplicate checks, ownership/category metrics, conditional counts, renderer-family counts, registry/publication counts, and the canonical A-L audit exist. | Current 20-artifact build/structural verification and manifest verification now pass; runtime/profile gates remain. |
| B — Quick Pack ownership | `IMPLEMENTED_UNVERIFIED` | Exactly six overlap capabilities are handed off; 23 capabilities remain PackForge-owned; mixed non-overlap behavior is retained; UI reports configured/effective ownership. | Current final-JAR structural ownership checks pass; required Quick Pack profiles remain unexecuted. |
| C — optional-mod detection | `IMPLEMENTED_UNVERIFIED` | Loader-neutral runtime detection and environment-gated profile reporter exist; detection failures fail conservatively. | Current Fabric/Forge/NeoForge Quick Pack profile launches and retained evidence. |
| D — registry graph | `VERIFIED_COMPLETE` | Registry generates 12 source families, 19 registry build targets (direct source nodes), 53 loader distributions, and a separate 62-cell exact runtime ledger. | Keep registry/direct-node bijection mandatory as graph evolves. |
| E — authoritative direct build | `IMPLEMENTED_UNVERIFIED` | All 53 loader distributions are authoritative direct cells; public graph is direct-only; contract validation is mandatory. `stonecutter-build.gradle` is validation/context only, the always-on direct helper owns leaf registration, and legacy wrapper parity is explicit opt-in. Fabric native preprocessing is proven for archive capture on 16 targets, SharedZip on 17, ReloadableResourceManager on 18 pre26 targets, RuntimeResourceHash on 17, LoadingOverlayToast on 14, Bitmap provider definition on all 19 targets, and SimpleReload on four targets (1.21.5 through 1.21.8). | Current 20-artifact build/structural verification and three representative direct/parity cells pass with slim MixinExtras; remaining all-53 parity, Java 17/21/25 proof, broader remap/refmap/JarJar parity, runtime, then removal of the optional parity oracle remain. |
| F — source ownership | `IMPLEMENTED_UNVERIFIED` | Zero exact/normalized duplicate groups; 15 target/version conditionals; zero target-key platform branches; registry-backed source policy; deterministic metric snapshot; and a current-tree ledger with 2,347 mandatory added LOC are recorded. | The adjusted shrink gate is 761 LOC short; retain the explicit failure and prevent branch/duplicate regression. |
| G — configuration renderers | `IMPLEMENTED_UNVERIFIED` | Unknown fields survive save; integer validation is shared; replacement-stage failure preserves the live snapshot and existing target while cleaning the temporary file; renderer families are reduced to three bodies plus two adapters; atlas retry configured/effective state is separated. | Live UI parity across entry routes/renderer families and shader/atlas runtime behavior. |
| H — range artifacts | `IMPLEMENTED_UNVERIFIED` | Registry expresses seven publication anchors and 20 loader artifacts without crossing maturity boundaries. | Current 1.4 exact 20-artifact build, structural verification, and release manifest verification pass; same-JAR proof for every exact release in each range remains. |
| I — compatibility profiles | `IMPLEMENTED_UNVERIFIED` | 36-profile catalog, 21 pinned available inputs, 11 dated unavailable dispositions, four pending records, profile reporter, schema-2 materializer, execution-scenario declarations, release preflight, three-loader transport, and deterministic fixtures. | Resolve four pending dispositions, then download/launch every available profile and retain current final-JAR evidence. |
| J — default-off evaluation | `VERIFIED_COMPLETE` | Eleven candidates frozen: five `SAFE_KEEP_DEFAULT_OFF`, six `FAILED_WITH_REASON`, zero promoted. | Runtime/performance gates only if a future promotion is proposed. |
| K — exact final-artifact matrix | `IMPLEMENTED_UNVERIFIED` | Existing-manifest verifier, exact-cell controller, provenance transport, nine deterministic fixtures with a hash-visible marker, fixture/scenario/release-manifest preflight, and a shared resolved-resource hash contract exist. Controlled Fabric/Forge/NeoForge smoke PASS lines carry a deterministic hash, the matrix binds it into resumable records, and exact cells require ten reloads. | Current 20-JAR manifest plus focused one-reload final-JAR smokes pass; full 62-cell ten-reload/fixture/exit proof and cancellation execution remain. |
| L — final reconciliation | `IMPLEMENTED_UNVERIFIED` | Current-status docs distinguish historical 62-cell, Quick Pack, manifest, and hash evidence from current structural implementation; this batch reconciles the direct/parity, source-ledger, profile, fixture, slim-MixinExtras, and manifest status. | Final evidence and rollback checkpoint after H/K and required profiles pass. |

## Current build architecture

`gradle/minecraft-targets.json` remains schema v2 and records 22 exact Minecraft releases, 12 source families, and 19 registry build targets (direct source nodes). Registry expansion creates 53 loader-specific direct distributions. The 62-cell ledger is a runtime acceptance matrix, not a build-node count.

Direct loader leaves are authoritative and public aggregate tasks are direct-only. `validateStonecutterDirectContract` rejects delegation in public leaves and checks registry metadata. Native Stonecutter preprocessing is focused compile- and source-archive-proven for Fabric archive capture across 16 targets, SharedZip across 17, ReloadableResourceManager across 18 pre26 targets, RuntimeResourceHash across 17, LoadingOverlayToast across 14, Bitmap provider definition across all 19, and SimpleReload across 1.21.5 through 1.21.8; it is not yet the general source transport. The always-on direct helper owns the public Stonecutter leaf; the nested Gradle wrapper exists only in the explicit legacy parity helper. Broader preprocessing ownership and full all-cell parity remain open.

## Current source and configuration evidence

Latest deterministic snapshot:

```text
productionFiles=217
productionLoc=16577
bridgeFiles=110
bridgeLoc=5553
bridgePercent=33.50
exactDuplicateGroups=0
normalizedDuplicateGroups=0
targetVersionConditionalLines=15
targetKeyLiteralReferences=103
platformTargetConditionalLines=0
stonecutterConditionalBlocks=10
maxStonecutterConditionalBlockLines=40
rendererBodies=3
rendererAdapters=2
publicationArtifacts=20
```

These metrics prove checked-in source shape only. They do not prove all target compilation or current artifact runtime.

Configuration persistence preserves unknown JSON fields. Shared renderer/model code centralizes categories, option state, validation, configured/effective values, ownership, and save behavior. A focused replacement-stage failure test proves the installed configuration and existing nonempty target survive while the temporary file is cleaned. Atlas retry remains default-off and its configured value is preserved; shader presence guards effective availability instead of mutating saved configuration. Focused tests passed, but no live renderer-family or shader profile was run.

## Current compatibility harness

The compatibility catalog contains 36 recipes:

- Twenty-one `AVAILABLE`/`UNTESTED` recipes with exact loader-family artifacts, dependency sets, runtime IDs, versions, URLs, and SHA-256 pins.
- Eleven exact-loader profiles marked `UNAVAILABLE` with dated public-source evidence; the three ResourcePackUnbounded 1.21.1 recipes remain among them.
- Four recipes remain `PENDING_METADATA`/`UNTESTED`. Fabric Quick Pack profiles are among them because public 1.21.1 builds require Fabric Loader `>=0.17.3`, while the exact PackForge cell uses `0.15.11`.

Every available recipe is pinned by URL/version/SHA-256 and requires runtime evidence markers. Twenty unique newly added pins were independently checked against their SHA-256 and embedded loader metadata; no downloaded artifacts were retained. `CompatibilityProfileReporter` reports profile ID, loader, target, observed mods, Quick Pack state, six handed-off capabilities, and 23 retained capabilities only when profile environment variables are present.

Schema-2 materialization validates the catalog before resolving inputs, uses a hash-addressed cache, derives safe decoded basenames, verifies SHA-256, transports fixture and expected-marker metadata, and rejects unsupported paths. Offline materialization and AST tests pass. Exactly five safe boolean overrides are merged against a complete 50-field v12 baseline; unsupported keys and values fail closed. No third-party compatibility-profile download or profile execution belongs to this evidence; the separate three-loader slim-MixinExtras smoke is recorded in `final-validation.md`. ImmediatelyFast-only path proof remains unavailable.

Nine deterministic 1.21.1 fixtures cover normal, high-entry-count, shader, connected-textures, CIT, entity, font-heavy, model-heavy, and mipmap-heavy packs. Each includes a stable `example`-namespace texture marker for positive resolved-resource hashing. Manifest contracts also cover overlay/namespace/duplicate and malformed-but-ZIP-readable entries. Fixture generation is deterministic; runtime, repeated reload, and cancellation evidence remain open.

## Current default-off decisions

No production default changed. Candidate catalog contains exactly 11 entries:

- Five `SAFE_KEEP_DEFAULT_OFF`: ZIP read pool, font bitmap cache, sprite decode, startup executor, and atlas retry.
- Six `FAILED_WITH_REASON`: atlas mip parallelism is `NOT_STARTED`; adaptive/duplicate model work and three startup async paths are `PARTIAL`.
- Zero promoted candidates.

All six required runtime/performance evidence classes remain `NOT_RUN`. `SAFE_KEEP_DEFAULT_OFF` means conservative non-promotion, not successful benchmark or compatibility proof.

## Current release boundary

Registry metadata expects 20 artifacts and 62 exact runtime cells. The current local directory contains exactly 20 version-1.4 JARs; `verifyAllArtifacts` and `verifyReleaseManifest` pass, and `inspectArtifactSizes` reports the final nested slim artifacts without duplicate ZIP entries. No current same-binary range, full exact matrix, or release-ready hash list is claimed. The preferred final count remains 17 or fewer only if fresh same-binary proof permits it.

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
