# Stable checkpoints

Checkpoint entries are chronological and must include commit SHA, parent, scope, exact verification, result, and rollback instruction.

## 1.4 artifact-hardening checkpoint

- Follow-up repair SHA: `8aa6844` (parent `6591e79`); content-aware nested-JAR scan and direct-shading rejection pass on the three representative artifacts.
- Commit SHA: `6591e79`
- Parent SHA: `014f71a`
- Scope: enforce one exact loader-specific MixinExtras `0.5.4:slim` artifact, recursive Forge common-runtime metadata/classes, required mixin/runtime entries, 128x128 <=32KiB icon, and size-report icon output.
- Verification: `validateTargetRegistry`; representative Fabric/Forge/NeoForge `mc1_21_1` direct builds; `verifyTargetArtifacts`; `inspectArtifactSizes`; refreshed `updateSourceMetricsCheckpoint`.
- Result: PASS for the three fresh representative artifacts; source metrics report 209 files / 15,814 LOC and zero Phase F shortfall. The mixed root 20-file directory remains stale in 17 files; full all-20/current manifest/runtime evidence is not claimed, and the bounded PackIndex benchmark timed out before samples.
- Rollback: `git revert 6591e79` removes only artifact verification/report hardening.

## Baseline documentation

- Commit SHA: `71b6ba396b60b79ed0cf2bdfb8c31ab7f7c76ce0`
- Parent SHA: `609270f533666e7636d11f7d16590be925ec836f`
- Scope: baseline evidence and migration rollback documents only
- Verification: `validateTargetRegistry`, clean `buildAllSupported`, direct `verifyExistingArtifacts`, focused tests, PackIndex benchmark; runtime smoke limitations recorded
- Status: `FOCUSED_VERIFIED`
- Rollback: no functional behavior changed; revert the baseline documentation commit if necessary

## Registry schema v2

- Commit SHA: `8d53b8abd996b6d03a0cf135325144db5748c985`
- Parent SHA: `bfd63ce2549f81731f5ba05a4e7cfeeeeb627c20`
- Scope: schema v2 fields, checked-in 22-release sequence/cells, maturity policy, and resolved-matrix task
- Verification: `./gradlew.bat validateTargetRegistry printResolvedMatrix buildTarget -Ppackforge_target=mc1_21_1 --no-daemon --stacktrace`; final focused build and all three child artifact verifiers passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert the registry checkpoint; schema v1 baseline is parent commit plus the documentation checkpoint

## Stonecutter current-target pilot

- Commit SHA: `e078d00e1b4ffcbb32afc8667f81b99f0ae8fa3b`
- Parent SHA: `8d53b8abd996b6d03a0cf135325144db5748c985`
- Scope: Stonecutter 0.9.7 settings integration and a parallel `mc1_21_1` current-target build path
- Verification: `:mc1_21_1:tasks`; `:mc1_21_1:verifyStonecutterCurrentTarget`; Fabric, Forge, and NeoForge child builds plus exact three-artifact check
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to return to the registry-only build; the legacy root aggregator remains available in the parent

## Central capability ownership and feature policy

- Commit SHA: `de3138344c633ca68b450e9688d0adb556c3de83`
- Parent SHA: `5f3ee749fe080f2f6035d9b5b0adf9a6924d1a3d`
- Scope: immutable `FeaturePolicy`, compatibility `FeatureFlags` facade, and reload-boundary policy capture
- Verification: focused Fabric 1.21.1 test suite; 106 tests completed successfully
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to return to the Stonecutter pilot; capability declarations and public feature-flag calls remain in the parent

## Stonecutter root verification wiring

- Commit SHA: `80a35d7bff2a186bf62a3696dc54dcf2dc639e14`
- Parent SHA: `5f3ee749fe080f2f6035d9b5b0adf9a6924d1a3d`
- Scope: restore repository-level registry/release tasks on the Stonecutter root and gate the current-target verifier on registry validation
- Verification: `./gradlew.bat :validateTargetRegistry`; `:mc1_21_1:verifyStonecutterCurrentTarget`
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this build-wiring repair; the Stonecutter pilot remains available but root verification would no longer be attached

## Archive and reload implementation deduplication

- Commit SHA: `723c504ca5ec2012e8535efc15bd96d4fbec8854`
- Parent SHA: `80a35d7bff2a186bf62a3696dc54dcf2dc639e14`
- Scope: shared 1.21.1/1.21.4/1.21.8 archive/reload mixins, platform source roots, and effective-source ownership validation
- Verification: root registry guard plus `buildTarget` for 1.21.1, 1.21.4, and 1.21.8; all three loader artifact verifiers passed for each target
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore per-version 1.21.1/1.21.4/1.21.8 hook copies

## Shared 1.21 client bootstrap

- Commit SHA: `e15103a52b52308ed7648cd27e6ff32f4e7ad24d`
- Parent SHA: `b97fc31fc9a978c99c171904c0036350d8f49012`
- Scope: shared 1.21.1/1.21.4/1.21.8 `PackForgeClient` reset bootstrap and exact-one effective-source validation
- Verification: root registry guard and all-loader `buildTarget` for 1.21.1, 1.21.4, and 1.21.8
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore the three version-local client bootstrap copies

## Quick Pack ownership

- Commit SHA: `35c47274fa5ccaf7b340327a164c0ebd5effaea7`
- Parent SHA: `b532d3dfe2d72461605db1f1467f95a0044d2da1`
- Scope: public Quick Pack detection, conservative ownership policy, and effective-state model
- Verification: focused policy tests and Fabric target test suite passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to remove the external ownership policy while retaining the prior feature policy

## Quick Pack early gating

- Commit SHA: `2759979bb93afc9ec62703a2ca08d429393e870b`
- Parent SHA: `35c47274fa5ccaf7b340327a164c0ebd5effaea7`
- Scope: loader-specific early Mixin suppression for Quick Pack overlap paths
- Verification: current loader builds and descriptor/plugin artifact checks passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore the pre-gating mixin selection

## Unified configuration

- Commit SHA: `e460da0e39f744544b4daf2ceb9844c87282e062`
- Parent SHA: `2759979bb93afc9ec62703a2ca08d429393e870b`
- Scope: central option schema, effective-state reporting, categories, search/paging, reset/save behavior, and native screen bridges
- Verification: current configuration tests and existing target builds passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore the prior screen implementations

## Exact-release registry and source-family ledger

- Commit SHA: `cd87525fbe763a313a607d2d15183b02657d0ecd`
- Parent SHA: `e460da0e39f744544b4daf2ceb9844c87282e062`
- Scope: schema-v2 exact release cells, twelve source families, maturity policy, capability contracts, and machine-readable matrix output
- Verification: `validateTargetRegistry` and `printResolvedMatrix` passed with all 22 required release IDs
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to the prior target registry schema

## Stonecutter current-target expansion

- Commit SHA: `e3f6f008965fd127795b2d4abe8efaff41f407af`
- Parent SHA: `cd87525fbe763a313a607d2d15183b02657d0ecd`
- Scope: current Stonecutter anchors plus the focused 1.20.2 node
- Verification: current Stonecutter target tasks and verifiers passed; 1.20.2 remained a focused feasibility cell
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to remove the added Stonecutter node while leaving the prior current anchors

## Planned 1.20.2 bridge

- Commit SHA: `0dc06bc2a6e684e7efd3d1df5f3f0f1aba6a7db0`
- Parent SHA: `e3f6f008965fd127795b2d4abe8efaff41f407af`
- Scope: 1.20.2 registry/platform coordinates, thin constructor/sprite/UI source bridge, target descriptor/access widener, and exclusion of planned targets from published aggregation
- Verification: Fabric and Forge `clean build` passed; focused JAR contents include the bridge and target descriptor. NeoForge is blocked before source compilation by two bounded toolchain attempts.
- Status: `FOCUSED_VERIFIED` for Fabric/Forge compile/package only; release cell remains `planned`
- Rollback: revert `862022c` first, then this checkpoint

## 1.21.11 descriptor repair

- Commit SHA: `862022c40249155f1d43fb55d97f3c4cb132c136`
- Parent SHA: `0dc06bc2a6e684e7efd3d1df5f3f0f1aba6a7db0`
- Scope: remove an invalid shared archive bridge reference from the version-local 1.21.11 descriptor
- Verification: Fabric 1.21.11 `clean build` passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this descriptor-only repair

## Exact 1.20.3 and 1.20.4 source-family cells

- Commit SHA: `948e437`
- Parent SHA: `42838f2`
- Scope: exact 1.20.3/1.20.4 registry cells, Stonecutter nodes, shared-family bridge, loader source selection, and NeoForge 20.4 config-screen API bridge
- Verification: `validateTargetRegistry` PASS; 1.20.4 all-loader production builds plus `verifyMc1_20_4Artifacts` PASS; 1.20.3 Fabric/Forge production builds PASS; NeoForge 1.20.3 blocked before compile by the official 20.3 artifact’s missing ModDev bundle capability
- Status: `FOCUSED_VERIFIED` for the declared build/package scope; both exact releases remain planned pending every required loader and runtime evidence
- Rollback: revert `948e437` to remove the two exact cells and family wiring while retaining the prior 1.20.2 feasibility checkpoint

## Exact 1.20.5 and 1.20.6 Java21 source-family cells

- Commit SHA: `21524f3`
- Parent SHA: `1569eff`
- Scope: exact 1.20.5/1.20.6 registry cells, Stonecutter nodes, Java21 shared-hook source selection, family-local 1.20.x client/test compatibility sources, and target descriptor/access widener
- Verification: `validateTargetRegistry` PASS; 1.20.5 Fabric `buildMc1_20_5` plus `verifyMc1_20_5Artifacts` PASS; 1.20.6 Fabric/Forge/NeoForge `buildMc1_20_6` plus `verifyMc1_20_6Artifacts` PASS
- Status: `FOCUSED_VERIFIED` for build/package scope; both exact releases remain planned pending production startup, deterministic reload, semantic hash, Quick Pack, and clean-exit evidence
- Rollback: revert `21524f3` to remove the Java21 exact cells while retaining the 1.20.3/1.20.4 family checkpoint

## Exact 1.21, 1.21.2, and 1.21.3 source-family cells

- Commit SHA: `71de8fe`
- Parent SHA: `3c52d21`
- Scope: exact 1.21/1.21.2/1.21.3 registry metadata, official loader availability, Stonecutter nodes, and shared 1.21 source selection
- Verification: `validateTargetRegistry` PASS; 1.21 all-loader `buildMc1_21` plus `verifyMc1_21Artifacts` PASS; 1.21.2 Fabric/NeoForge `buildMc1_21_2` plus `verifyMc1_21_2Artifacts` PASS; 1.21.3 all-loader `buildMc1_21_3` plus `verifyMc1_21_3Artifacts` PASS
- Status: `FOCUSED_VERIFIED` for build/package scope; all three exact releases remain planned pending production startup, deterministic reload, semantic hash, Quick Pack, and clean-exit evidence
- Rollback: revert `71de8fe` to remove the exact 1.21-family cells while retaining the Java21 source-family checkpoint

## Phase 12 — exact matrix wiring and toolchain repair

- Date: 2026-08-12
- Commit SHA: `b71e281dc7f4318c872982d6dcdf9e5e74508116`
- Parent stable checkpoint: `a17d2ca6d5f0926746b9b2d721bec9f572b67db5`
- Status: `FOCUSED_VERIFIED`; final artifact-range publication remains open
- Scope: registry-derived CI and release-manifest generators, official Mojang release-sequence provenance guard, production smoke wrappers, Forge Java17 descriptor/resource repair, legacy NeoForge wrapper/toolchain path, and modern NeoForge JarJar packaging
- Commands: `scripts/Verify-Mojang-ReleaseSequence.ps1`; `gradlew.bat validateTargetRegistry printResolvedMatrix --no-daemon --console plain --stacktrace`; `gradlew.bat build --no-daemon --console plain --stacktrace`; `python scripts/Generate-CiMatrix.py` for build/smoke/publish-smoke/publish; `python scripts/Generate-ReleaseManifest.py --artifacts-dir build/libs --output-dir build/libs/release`
- Results: Mojang guard PASS for all 22 release IDs and recorded SHA-256; registry PASS; matrix counts 19/62/26/17; root build PASS in 4m22s with `87 actionable tasks: 24 executed, 63 up-to-date`; current public 17-artifact verifier PASS; modern NeoForge 26.x all-in-one contains MixinExtras exactly once; release manifest regenerated with 17 artifacts
- Compatibility: recorded exact runtime evidence covers 62 officially available loader cells and the real Quick Pack 1.5.0 Fabric profile passes ten reloads; unrelated third-party pairwise profiles remain `UNTESTED`
- Deviation: 13 non-anchor pre-26 release rows remain `planned-verified`; they are not promoted by status-only change because no same-binary range proof is recorded for their public artifact metadata. A fresh production smoke of the rebuilt modern NeoForge 26.x artifact remains open.
- Rollback: `git revert b71e281dc7f4318c872982d6dcdf9e5e74508116` restores the previous target-specific build/publication wiring while retaining earlier version-family commits.
- Next mandatory phase: same-binary pre-26 range proofs, bounded artifact consolidation/publication, current rebuilt NeoForge 26.x production smoke, then final full-matrix evidence commit.

## First proven range artifact

- Date: 2026-08-12
- Commit SHA: `fbd7b3208347230ae17e90f822bde2e100e82992`
- Parent stable checkpoint: `0d5dc3ab0820d34fd63d709840696cdbb2335a1d`
- Status: `RANGE_VERIFIED`
- Scope: one Fabric, Forge, and NeoForge artifact for Minecraft 1.20.2-1.20.4; Forge 48/49 pre-application reload-observer selection; loader-specific artifact-range plumbing; final-JAR target-marker and interior-range smoke validation.
- Verification: all nine exact runtime cells PASS with two deterministic reloads and clean exit; hashes Fabric `77D690FF...`, Forge `88F4BB16...`, NeoForge `24460EEF...`; clean root build PASS with 107 executed tasks; post-clean rebuild reproduced the full hashes recorded in `artifact-consolidation.md`.
- Publication projection: registry-derived counts `build=19`, `smoke=62`, `publish-smoke=35`, `publish=20`; exactly 20 staged artifacts accepted by the release-manifest verifier.
- Rollback: `git revert fbd7b3208347230ae17e90f822bde2e100e82992`.

## Second proven range artifact

- Date: 2026-08-12
- Commit SHA: `5ed1480540162547b3de475e0e9a51e7965d968c`
- Parent stable checkpoint: `9b060a267e525eda3ce5f47852064fa1e546b079`
- Status: `RANGE_VERIFIED`
- Scope: one Fabric artifact for Minecraft 1.20.5-1.21.1; one Forge and NeoForge artifact for 1.20.6-1.21.1; registry-owned loader descriptor compatibility; version-independent Quick Pack overlap-module handoff; bounded Fabric clean-shutdown grace after completed reload proof.
- Verification: all ten exact runtime cells PASS with two deterministic reloads and clean exit; hashes Fabric `99D38B65...`, Forge `CA9F0EEA...`, NeoForge `969BFADD...`; real Quick Pack 1.5.0 profile PASS with `MODULE_HANDOFF`, ten requested reloads, and clean exit; full clean build PASS in 8m49s and reproduces all tested hashes.
- Publication projection: registry-derived counts `build=19`, `smoke=62`, `publish-smoke=42`, `publish=20`; exactly 20 clean-built artifacts accepted by the release-manifest verifier.
- Compatibility policy: Quick Pack version is diagnostic only. Presence delegates only `RESOURCE_PACK_INDEX`, `ZIP_READ_POOL`, `FONT_PROVIDER_PRESELECTION`, `ATLAS_MIP_PARALLEL`, `LOADING_FADE_CONTROL`, and `LOADING_STATUS_OVERLAY`; unrelated PackForge modules retain normal policy. Quick Pack 1.4 and older are best-effort/not guaranteed.
- Rollback: `git revert 5ed1480540162547b3de475e0e9a51e7965d968c`.

## Backfilled exact-family implementation checkpoints

These bounded implementation commits preceded the integrated exact-matrix checkpoint. They are retained separately so each completed implementation unit has an explicit parent, scope, verification state, and rollback command.

### Minecraft 1.21.5-1.21.7 targets

- Commit SHA: `37e6e7ce3305a34c1b06e6ab684c6d008047e3e6`
- Parent SHA: `764f9a53819b744e108ca5757e9579d126035c44`
- Scope: exact registry rows, Stonecutter nodes, and loader source selection for Minecraft 1.21.5, 1.21.6, and 1.21.7.
- Verification: focused target builds completed; later exact-matrix checkpoint `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` proves every applicable final-artifact runtime cell in the widened 1.21.5-1.21.8 family.
- Status: `SUPERSEDED_BY_FULL_MATRIX_VERIFIED`
- Rollback: revert later dependent commits first, then `git revert 37e6e7ce3305a34c1b06e6ab684c6d008047e3e6`.

### Minecraft 1.21.9-1.21.10 targets

- Commit SHA: `41bbc54de1e14142307c0342e254e10e13360627`
- Parent SHA: `37e6e7ce3305a34c1b06e6ab684c6d008047e3e6`
- Scope: exact registry rows, Stonecutter nodes, loader source selection, and the first 1.21.9/1.21.10 font-provider compatibility sources.
- Verification: focused target builds completed; later exact-matrix checkpoint `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` proves every applicable final-artifact runtime cell in the widened 1.21.9-1.21.11 family.
- Status: `SUPERSEDED_BY_FULL_MATRIX_VERIFIED`
- Rollback: revert later dependent commits first, then `git revert 41bbc54de1e14142307c0342e254e10e13360627`.

### Exact 26.x smoke hardening

- Commit SHA: `e03c4b29c1928758d4a28ee091d06ed8c36734d1`
- Parent SHA: `41bbc54de1e14142307c0342e254e10e13360627`
- Scope: exact 26.x production-smoke lifecycle, loader mod-list compatibility, Quick Pack/GUI plugin guards, and process evidence collection.
- Verification: focused smoke harness checks completed; final 26.1, 26.1.1, 26.1.2, and 26.2 cells are included in the 62/62 final-artifact result.
- Status: `FULL_MATRIX_VERIFIED`
- Rollback: revert later smoke-harness dependants first, then `git revert e03c4b29c1928758d4a28ee091d06ed8c36734d1`.

### Minecraft 1.21.9-1.21.10 runtime adapters

- Commit SHA: `f1552b27f33632d5966454bf8cc06927fc10fdc1`
- Parent SHA: `e03c4b29c1928758d4a28ee091d06ed8c36734d1`
- Scope: atlas, loading-overlay, and reload-observer bridges plus loader wiring for Minecraft 1.21.9 and 1.21.10.
- Verification: focused builds and smoke repair checks completed; final same-JAR proof covers all applicable 1.21.9, 1.21.10, and 1.21.11 cells.
- Status: `FULL_MATRIX_VERIFIED`
- Rollback: revert later dependent commits first, then `git revert f1552b27f33632d5966454bf8cc06927fc10fdc1`.

### Minecraft 1.21.5-1.21.7 runtime seams

- Commit SHA: `a17d2ca6d5f0926746b9b2d721bec9f572b67db5`
- Parent SHA: `f1552b27f33632d5966454bf8cc06927fc10fdc1`
- Scope: reload-observer and loader/runtime compatibility seams for Minecraft 1.21.5 through 1.21.7.
- Verification: focused smoke repair checks completed; final same-JAR proof covers all applicable 1.21.5 through 1.21.8 cells.
- Status: `FULL_MATRIX_VERIFIED`
- Rollback: revert later dependent commits first, then `git revert a17d2ca6d5f0926746b9b2d721bec9f572b67db5`.

## First-range publication wiring

- Commit SHA: `9b060a267e525eda3ce5f47852064fa1e546b079`
- Parent SHA: `fbd7b3208347230ae17e90f822bde2e100e82992`
- Scope: publish the proven 1.20.2-1.20.4 range through registry-derived publication metadata and manifest validation.
- Verification: `publish-smoke=35`, `publish=20`, and the exact 20-artifact manifest verifier passed at this checkpoint; later final evidence covers all 62 publication-smoke cells.
- Status: `SUPERSEDED_BY_FULL_MATRIX_VERIFIED`
- Rollback: revert later range commits first, then `git revert 9b060a267e525eda3ce5f47852064fa1e546b079`.

## Remaining range candidates and full exact matrix

- Date: 2026-08-12
- Commit SHA: `051aaaca42bdc980a3260d1a6c6fcd4404f228b7`
- Parent SHA: `9498f45c4b92b97d6ff5f19ca5864488c8a33ab1`
- Scope: final 1.21.2-1.21.4, 1.21.5-1.21.8, and 1.21.9-1.21.11 range artifacts; capability/mixin floors; runtime version bridges; registry-derived seven-family build matrix; resumable exact-production controller; version-independent Quick Pack module handoff.
- Verification: seven-family aggregate build and artifact verification completed; exactly 20 candidates produced; 62/62 exact release/loader cells passed startup, two reloads, semantic/resource evidence, and clean exit; generated counts are `build=7`, `smoke=62`, `publish-smoke=62`, and `publish=20`.
- Status: `FULL_MATRIX_VERIFIED`
- Rollback: revert `a3402866b217ac159d6a3cec70d3585028f79732` first, then `git revert 051aaaca42bdc980a3260d1a6c6fcd4404f228b7`.

## Canonical source consolidation and final hash binding

- Date: 2026-08-12
- Commit SHA: `a3402866b217ac159d6a3cec70d3585028f79732`
- Parent SHA: `051aaaca42bdc980a3260d1a6c6fcd4404f228b7`
- Scope: registry-selected canonical shared Java classes, removal of copied production classes, deterministic source metrics and CI gate, isolated Fabric native roots, final row promotion, and exact-matrix controller hardening.
- Verification: `reportSourceMetrics validateTargetRegistry verifyExistingArtifacts` PASS against 20 clean-built artifacts; source metrics report 16,209 LOC, 33.48% bridge LOC, zero normalized duplicate classes, and 100% baseline duplicate-group reduction. Eighteen artifacts remained byte-identical and retained 58-cell proof; the four cells represented by the two changed Forge hashes reran 4/4 PASS. The final Quick Pack 1.5.0 profile passed ten reloads with `MODULE_HANDOFF` and natural clean exit.
- Status: `FULLY_VERIFIED`
- Rollback: `git revert a3402866b217ac159d6a3cec70d3585028f79732` restores the prior source ownership and publication ledger.

## Continuation baseline and deterministic source gates

- Date: 2026-08-12
- Commit SHA: `71f29b116f40e967996fa5e75e45aad89a7b0bb6`
- Parent SHA: `9baa02f5bb65277aafc7e508c6f0e1c6c9a261a5`
- Scope: current-state audit; deterministic handwritten-source, ownership, branch, renderer, registry-cell, and publication metrics; explicit stable-checkpoint snapshot refresh; exact and normalized duplicate gates.
- Verification: `./gradlew.bat updateSourceMetricsCheckpoint validateTargetRegistry --no-daemon --console=plain` passed; a normal `reportSourceMetrics` rerun preserved checked-in snapshot timestamps and hashes; JSON and Markdown contain LF-only portable bytes; baseline `buildAllSupported verifyAllArtifacts` completed successfully in 12 minutes 1 second with 75 actionable tasks.
- Result: 202 production Java files, 16,209 nonblank LOC, 99 aggregate bridge files / 5,426 LOC, zero exact or normalized duplicate groups, 80 platform `target.key` references, 84 target-key/version conditional lines, four native configuration renderer paths, 22 exact releases, 62 loader cells, 19 registered build targets, and 20 publication artifacts.
- Status: `PHASE_A_VERIFIED_COMPLETE`; build/package evidence only, with runtime/profile limitations classified in `current-state-audit.md`.
- Rollback: `git revert 71f29b116f40e967996fa5e75e45aad89a7b0bb6` removes the continuation audit and expanded source gates while retaining all earlier implementation history.

## Operation-level Quick Pack ownership

- Date: 2026-08-12
- Commit SHA: `0c2fb7bcff5dd634254f3ad25b205b896acfe34e`
- Parent SHA: `bd542a1f286be99d16772c903dfd329e37632717`
- Scope: loader-neutral optional-mod metadata after bootstrap; exact six-capability Quick Pack handoff; operation-level archive, font, loading-overlay, toast, and startup-status mixins; retained loader observations; packaged-plugin and internal-reference guards.
- Verification: source metrics and registry gates passed with 207 files, 16,129 LOC, zero exact/normalized duplicates, and zero Quick Pack internal references. Focused policy/UI/timing tests passed on Fabric, Forge, and NeoForge. All-loader builds and artifact-contract verification passed for 1.20.1, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.9, 1.21.10, 1.21.11, and 26.1-26.2.
- Status: `IMPLEMENTATION_AND_PACKAGE_VERIFIED`; real Quick Pack final-JAR profiles remain required before Phase C runtime completion.
- Rollback: `git revert 0c2fb7bcff5dd634254f3ad25b205b896acfe34e` restores early whole-mixin suppression and the preceding compatibility policy.

## Compatibility-profile evidence harness

- Date: 2026-08-12
- Commit SHA: `3840a63bb8361cadd216ad15853efb4428ac29d1`
- Parent SHA: `017e5209ae6a6d59180416d93943d5afaf33ab1a`
- Scope: scalar JSON compatibility-profile transport; additional-mod isolation and SHA-256 provenance for Fabric, Forge, and NeoForge; expected/forbidden log markers; immutable hash-addressed PackForge artifacts, third-party JARs, and fixtures; evidence-bound resume and summaries.
- Verification: all three PowerShell scripts parsed with zero AST errors; a one-cell/two-marker dry plan resolved the expected dependency and profile counts; duplicate and unknown cell selections were rejected; focused static contracts passed; two independent read-only reviews found no remaining harness blocker.
- Status: `HARNESS_VERIFIED_RUNTIME_PENDING`; no Minecraft client was launched for this checkpoint, so it records controller integrity rather than a completed third-party compatibility profile.
- Rollback: `git revert 3840a63bb8361cadd216ad15853efb4428ac29d1` removes compatibility-profile transport and evidence hardening while retaining operation-level Quick Pack ownership.

## Registry-derived Stonecutter project graph

- Date: 2026-08-12
- Commit SHA: `8241e600d807f3900224e90a17d3467413d30ee1`
- Parent SHA: `f6df8d85e079e278566fe61f81b438168df574b4`
- Scope: registry-derived Stonecutter loader branches and target distributions; separate 19-target source-anchor, 53-distribution, and 62 exact runtime-cell ledgers; explicit VCS target authority; cross-platform delegated-wrapper execution; stale delegated-artifact prevention.
- Verification: `projects` exposed exactly three loader branches and 53 target-loader leaves. `updateSourceMetricsCheckpoint validateStonecutterRegistry` passed with 19 unique source anchors, 53 distributions, and 62 exact loader cells. A Fabric 1.21.1 leaf regenerated and verified its distribution artifact after the generic freshness hardening. A legacy NeoForge 1.20.2 leaf built and verified through the retained rollback path before that loader-neutral freshness tweak. Independent re-review found no remaining technical blocker.
- Status: `PHASE_D_VERIFIED_COMPLETE`; this proves registry graph ownership and delegated rollback behavior. Direct in-graph loader builds remain Phase E work, and no Minecraft runtime result is implied.
- Rollback: revert later direct-build commits first, then `git revert 8241e600d807f3900224e90a17d3467413d30ee1` to restore the prior manually enumerated Stonecutter project graph and settings.

## Direct Fabric Stonecutter pilot

- Date: 2026-08-13
- Commit SHA: `6125316572a086369320616ec754a8076068e29a`
- Parent SHA: `ce87201d22b4d2a04327fe227efba3bbe83fd174`
- Scope: registry-selected direct build for the Fabric `mc1_21_1` leaf; physical loader source roots; per-leaf target selection; public root build, collection, verification, and clean ownership; retained delegated parity oracle; fail-closed manifest provenance comparison.
- Verification: a clean direct build ran compilation, resource generation, tests, access-widener validation, remapping, and final packaging. Direct-versus-standalone parity matched all 214 ZIP entries after asserting exactly four Stonecutter provenance attributes. `-Ppackforge_target=mc1_21_1 verifyTargetArtifacts` passed all three loaders in 1 minute 23 seconds with 125 actionable tasks, using the direct Fabric leaf and delegated Forge/NeoForge fallbacks. The collected and leaf Fabric JARs both hashed `CB57A4FADACB3BD55F420234C7F148ABB68B810F14539DCD43F2380985C56DD1`. Root `clean` removed direct and standalone outputs, and the full-matrix dry run scheduled the direct Fabric leaf without its delegated build. Independent re-review found no remaining blocker.
- Status: `PHASE_E_FABRIC_PILOT_VERIFIED`; this is one authoritative target-loader cutover, not Phase E completion. Forge, NeoForge, and remaining Fabric leaves retain the delegated rollback path, and no Minecraft runtime result is implied.
- Rollback: `git revert 6125316572a086369320616ec754a8076068e29a` restores delegated ownership for this Fabric cell while retaining the registry-derived Phase D graph.

## Direct Fabric Stonecutter matrix implementation

- Date: 2026-08-13
- Commit SHA: `68866ccffae9a06d404d5a791dd4e68d089f9486`
- Parent SHA: `8c9925bd97c0801ccda9876e99892608aa622e12`
- Scope: registry-default direct ownership for all 19 Fabric leaves; a loader-neutral direct-build/parity helper; direct-only Stonecutter build and structural-verification aggregates; an explicitly separate delegated parity oracle; fail-closed loader-default validation.
- Verification: `validateStonecutterRegistry` passed with three branches, 19 source anchors, 53 distributions, and 62 exact loader cells. `buildStonecutterAll --dry-run` scheduled all 19 Fabric direct leaves and no delegated node build. Direct builds passed for Java 17 `mc1_20_1`, Java 21 `mc1_21_1` and special-source `mc1_21_9`, and Java 25 `mc26_1_to_26_2`. The targeted `mc1_21_9` direct-versus-delegated oracle matched all 213 ZIP entries. Independent review found no correctness blocker.
- Status: `IMPLEMENTED_REPRESENTATIVE_VERIFIED`; the full 19-leaf aggregate was stopped on request and is not counted as evidence. This checkpoint does not claim all-Fabric matrix verification, Phase E completion, or Minecraft runtime proof. Forge and NeoForge remain delegated registry cells.
- Rollback: `git revert 68866ccffae9a06d404d5a791dd4e68d089f9486` restores the single-cell Fabric pilot while retaining its verified direct path; revert `6125316572a086369320616ec754a8076068e29a` afterward only if the pilot must also return to delegated ownership.

## Direct Forge Stonecutter packaging pilots

- Date: 2026-08-13
- Commit SHA: `c28cc8cf62dbafa7cd3a52496f259848788c3a78`
- Parent SHA: `3b94a9852494652128919e54500293ca9e4ac3a2`
- Scope: direct Stonecutter ownership for Forge `mc1_20_1` and `mc1_21_1`; physical loader source roots; legacy SRG/refmap final renaming; modern JarJar packaging; loader-specific metadata and structural checks; retained delegated parity oracle.
- Verification: `validateStonecutterRegistry` passed. The `mc1_20_1` direct build generated and merged Mixin mappings, produced the legacy refmap, ran JarJar, and completed final renaming. The `mc1_21_1` direct build completed its modern JarJar path. The two collected artifacts then passed the root direct structural verifiers. Independent read-only review found no blocker.
- Status: `PHASE_E_FORGE_PILOTS_PACKAGE_VERIFIED`; this proves the two distinct Forge packaging paths, not Forge matrix parity, all-cell migration, or Minecraft runtime behavior. Other Forge cells remain delegated.
- Rollback: `git revert c28cc8cf62dbafa7cd3a52496f259848788c3a78` returns both Forge cells to delegated ownership without affecting the Fabric direct matrix.

## Guarded NeoForge Stonecutter packaging pilots

- Date: 2026-08-13
- Commit SHA: `2beb0bd821c51d37f2a4b93cb57293ce0be70d5f`
- Parent SHA: `b600888c3a400c0dafbdb53337d004bc22e98b8a`
- Scope: direct Stonecutter ownership for NeoForge `mc1_20_2` and `mc1_21_1`; root Foojay toolchain resolution; physical loader source roots; UserDev JarJar and ModDev jar authorities; a shared single-use NeoForge build service and ordered build pipelines protecting parallel multi-version graphs; retained delegated parity oracle.
- Verification: registry configuration passed. The Java 17 UserDev pilot completed NeoForm preparation, compilation, and JarJar packaging. The Java 21 ModDev pilot completed artifact preparation, compilation, and jar packaging. Both collected artifacts passed the root direct structural verifiers. After correcting an overbroad ordering edge, a dry run of root validation plus both explicit parity tasks passed without graph cycles. Independent re-review approved the final service and ordering design.
- Status: `PHASE_E_NEOFORGE_PILOTS_PACKAGE_VERIFIED`; actual direct-versus-delegated ZIP parity and Minecraft runtime execution were not run. Other NeoForge cells remain delegated.
- Rollback: `git revert 2beb0bd821c51d37f2a4b93cb57293ce0be70d5f` returns both NeoForge cells to delegation and removes their root toolchain/serialization wiring without affecting Fabric or Forge direct cells.

## All-loader direct Stonecutter ownership

- Date: 2026-08-13
- Commit SHA: `9b6e25512c4363829f3b1a5f86538cabf2797fa5`
- Parent SHA: `81ab1697e601d361af49333e449b6482f1c61fd1`
- Scope: registry-default direct ownership for all Fabric, Forge, and NeoForge cells; removal of redundant per-cell pilot overrides; authoritative 19/17/17 loader distribution.
- Verification: registry parsing resolved exactly 19 Fabric, 17 Forge, and 17 NeoForge direct leaves with no explicit overrides. `validateStonecutterRegistry` configured the entire graph and passed with three branches, 19 source anchors, 53 distributions, and 62 exact loader cells. The previously unproven Java 25 Forge and NeoForge `mc26_1_to_26_2` direct builds passed, followed by both root structural artifact verifiers. Earlier checkpoints supply representative Java 17/21 packaging and one Fabric ZIP-parity result.
- Status: `PHASE_E_DIRECT_OWNERSHIP_IMPLEMENTED_REPRESENTATIVE_VERIFIED`; all 53 leaves are authoritative direct tasks, but the complete direct aggregate, all-cell ZIP parity, and runtime matrix were intentionally deferred. Delegated tasks remain available only as explicit parity/rollback oracles.
- Rollback: `git revert 9b6e25512c4363829f3b1a5f86538cabf2797fa5` restores the Fabric default plus four verified Forge/NeoForge pilots. Revert loader pilot commits afterward only if those cells must also return to delegation.

## Forward-compatible configuration persistence

- Date: 2026-08-13
- Commit SHA: `782510c685db64a90d99e177c5d7531e07d9668f`
- Parent SHA: `f686046b89fee0cb3d60a730a774ccef536cf417`
- Scope: preservation of unknown root JSON members through load, detached screen drafts, sanitization, and atomic save; deep-copy isolation for nested unknown values; fail-fast uniqueness enforcement for configuration option IDs.
- Verification: the focused Fabric-hosted `PackForgeConfigPreservationTest` and `PackForgeConfigScreenModelTest` suites passed in 14 seconds with 14 tests, zero failures, and zero errors. The preservation suite covers unknown scalar, object, and array values across version-12 migration and `applyAndSave`; independent read-only review found no blocker.
- Status: `PHASE_G_CONFIG_SAFETY_VERIFIED`; renderer-family consolidation, structural entry-point coverage, and cross-family presentation parity remain open Phase G work.
- Rollback: `git revert 782510c685db64a90d99e177c5d7531e07d9668f` restores schema-12-only serialization and removes duplicate option-ID enforcement.

## Publication documentation consistency contract

- Date: 2026-08-13
- Commit SHA: `e0a644d3443fbecc37fef7537de96e5d9cee148a`
- Parent SHA: `782510c685db64a90d99e177c5d7531e07d9668f`
- Scope: README correction from the stale 17-artifact/six-target description to the registry-derived 20-artifact/seven-target publication set; exact loader availability and lower-bound rows for all 22 releases; a fail-closed README marker checked by `validateTargetRegistry`.
- Verification: a focused registry-to-README checker matched one publication marker, 20 loader-specific artifacts, seven publication target keys, and all 22 exact loader rows. Scoped whitespace validation passed, and independent re-review approved the corrected exact table and marker gate. No Gradle build or Minecraft runtime was run for this documentation-only contract.
- Status: `PHASE_H_DOCUMENTATION_CONSISTENCY_VERIFIED`; historical consolidation remains implemented but unverified against the new direct-build bytes until retained final-JAR same-artifact runtime evidence is regenerated.
- Rollback: `git revert e0a644d3443fbecc37fef7537de96e5d9cee148a` restores the prior README text and removes its registry consistency gate.

## Isolated README publication gate

- Date: 2026-08-13
- Commit SHA: `d21a2a5f0652b46afd92bde87725fb943e1842a3`
- Parent SHA: `af62f124f6938f8e3af464826ae6faf829edadfb`
- Scope: extract README publication declaration validation from `validateTargetRegistry` into an explicit reusable contract; retain fail-closed marker, artifact-count, and target-key checks.
- Verification: focused static registry-to-README contract check passed. No compile, package, or Minecraft runtime executed.
- Status: `PHASE_H_DOCUMENTATION_CONSISTENCY_VERIFIED`; extraction changes validation ownership only.
- Rollback: `git revert d21a2a5f0652b46afd92bde87725fb943e1842a3` restores the README gate inside `validateTargetRegistry`.

## Registry-owned source selection policies

- Date: 2026-08-13
- Commit SHA: `4c76d6520080834a3c278bbefd7c0ce31189da84`
- Parent SHA: `d21a2a5f0652b46afd92bde87725fb943e1842a3`
- Scope: seven registry-owned source policies and target assignments; shared policy interpreter replacing loader-local target/source-family selection while retaining source ordering, exclusions, source-set enablement, and `sourcesJar` duplicate policy.
- Verification: static golden source-selection comparison and independent read-only review passed. `validateStonecutterRegistry` passed in 34 seconds with 53 direct leaves and 62 exact loader cells. No compile, package, ZIP parity, or Minecraft runtime executed.
- Status: `PHASE_F_CONFIGURATION_VERIFIED`; not full build or runtime verification.
- Rollback: `git revert 4c76d6520080834a3c278bbefd7c0ce31189da84` restores loader-local source selection after reverting later dependants.

## Shared modern configuration renderer

- Date: 2026-08-13
- Commit SHA: `839928cb7ee2be3ebcab73db859e0c2a5fc34a84`
- Parent SHA: `cc9f51282b3d7d1b98cb6fa526ac5d394d288946`
- Scope: extract the shared modern native configuration renderer while retaining version-specific wrappers and entry points.
- Verification: Fabric `mc1_21_10 compileClientJava` and `mc1_21_11 compileClientJava` each passed in 10 seconds. Independent static review passed.
- Status: `PHASE_G_STRUCTURAL_CONTRACT_VERIFIED`; no live UI screenshot or Minecraft client runtime was executed.
- Rollback: after reverting later documentation and dependent checkpoints, `git revert 839928cb7ee2be3ebcab73db859e0c2a5fc34a84` restores version-local modern renderers.

## Integer validation parity and screen contract

- Date: 2026-08-13
- Commit SHA: `35a6b4ca39b8afc0a9d1a096a76780b463ff2b5e`
- Parent SHA: `839928cb7ee2be3ebcab73db859e0c2a5fc34a84`
- Scope: shared transient integer-input validation across legacy, shared modern, and 26.x renderers; Done/apply guards; reset/rebuild invalid-state lifecycle; structural screen-contract validation.
- Verification: focused Fabric `mc1_21_10` test passed in 13 seconds with five tests, zero failures, zero errors, and compilation. `mc1_20_1 compileClientJava` passed in 11 seconds; `mc26 compileClientJava` passed in 10 seconds. `Validate-ConfigScreenContract` passed with `bodies=3`, `wrappers=2`, `entryPoints=9`, and `sharedTargets=14`. Independent static reviews passed.
- Status: `PHASE_G_VERIFIED_COMPLETE_STRUCTURAL`; the plan explicitly permits structural renderer tests in place of screenshots, so the Phase G completion gate is met. No live UI screenshot or Minecraft client runtime was executed, and those must not be inferred from this checkpoint.
- Rollback: after reverting later documentation and dependants, revert `35a6b4ca39b8afc0a9d1a096a76780b463ff2b5e` first, then `839928cb7ee2be3ebcab73db859e0c2a5fc34a84`.

## Compatibility profile catalog foundation

- Date: 2026-08-13
- Commit SHA: `934c283695a099593134e178bbe4b26f274ffe1a`
- Parent SHA: `1de2afff16b333759946f805802de56019913bff`
- Scope: declarative compatibility-profile catalog covering 36 required isolated and high-risk recipes, with frozen registry-cell references and explicit metadata/evidence state.
- Verification: catalog AST and normal validator passed for 36 profiles, 36 frozen recipes, and valid registry-cell references. No external metadata resolution, artifact download, or profile execution occurred.
- Status: `PHASE_I_FOUNDATION_VERIFIED`; every catalog profile remains `PENDING_METADATA` and `UNTESTED`.
- Rollback: after reverting the two later Phase I checkpoints, `git revert 934c283695a099593134e178bbe4b26f274ffe1a` removes the catalog foundation only.

## Compatibility profile evidence validation

- Date: 2026-08-13
- Commit SHA: `b35a0b04305aed040d0ae6d9f203acb3a0f3d84f`
- Parent SHA: `934c283695a099593134e178bbe4b26f274ffe1a`
- Scope: fail-closed validation for profile availability, external dependency pins, and execution evidence; `AVAILABLE` materialization requires complete provenance and evidence.
- Verification: validator self-test accepted one positive `AVAILABLE` fixture and rejected 14 mutations; independent retests rejected fake pins and nonexistent evidence. No real metadata lookup, download, or smoke/profile launch occurred.
- Status: `PHASE_I_FOUNDATION_VERIFIED`; all 36 real catalog entries remain `PENDING_METADATA` and `UNTESTED`.
- Rollback: after reverting the runner-selection checkpoint, `git revert b35a0b04305aed040d0ae6d9f203acb3a0f3d84f` restores the prior catalog-only validation.

## Compatibility profile runner selection

- Date: 2026-08-13
- Commit SHA: `5f668bc597ffdc094c91fda645fde19e7f0ed5f2`
- Parent SHA: `b35a0b04305aed040d0ae6d9f203acb3a0f3d84f`
- Scope: catalog-backed profile selection for the exact-production runner, including duplicate/conflicting selector guards and fail-closed availability handling.
- Verification: runner AST validation passed. `Test-ExactProductionMatrixProfiles` passed in about six seconds, covering empty, unknown, catalog guard, duplicate, conflict, exact-cell, `PENDING_METADATA`, `UNAVAILABLE`, and `AVAILABLE` cases. The runner invokes the catalog validator directly; it is not a root Gradle or CI gate.
- Status: `PHASE_I_FOUNDATION_VERIFIED`; all 36 profiles remain `PENDING_METADATA` and `UNTESTED`. No real metadata resolution, download, or compatibility profile execution occurred.
- Rollback: `git revert 5f668bc597ffdc094c91fda645fde19e7f0ed5f2` first; then revert `b35a0b0` and `934c283` if the entire Phase I foundation must be removed.

## Existing release-manifest verification

- Date: 2026-08-13
- Commit SHA: `67a7bf5e57de4d35089983db00ae84328d530c7c`
- Parent SHA: `1914912723bcfa19742d58e3bb549d2c203ab5b5`
- Scope: add a non-writing `--verify-existing` mode to `Generate-ReleaseManifest.py`; make `verifyReleaseManifest` verify the already-generated release manifest after structural artifact verification; add a temporary-fixture self-test for manifest equality, artifact set, ordering, sizes, and SHA-256/SHA-512 values.
- Verification: generator self-test and Python compilation passed; independent read-only review passed. Current `--verify-existing` check reported `CURRENT_MANIFEST_MISSING`: only 7 of 20 expected release JARs existed and `build/libs/release/manifest.json` was absent.
- Status: `PHASE_K_MANIFEST_VERIFIER_STRUCTURAL_VERIFIED`; real `verifyReleaseManifest` was not run. No full artifact build, final-manifest verification, exact release matrix, compatibility profile, or Minecraft runtime proof exists from this checkpoint. Phase K remains incomplete.
- Rollback: after reverting later documentation-only checkpoints, `git revert 67a7bf5e57de4d35089983db00ae84328d530c7c` removes the existing-manifest verifier and its self-test, returning to generated-manifest-only behavior.

## Atlas retry compatibility guard

- Date: 2026-08-13
- Commit SHA: `943354a4897011b62b4c4cab958afbc92fd16035`
- Parent SHA: `67a7bf5e57de4d35089983db00ae84328d530c7c`
- Scope: preserve the configured atlas-retry value while shader compatibility changes only its effective state; keep the guard option itself visibly effective; remove the mc26 startup mutation of persisted configuration.
- Verification: focused Fabric `mc26_1_to_26_2` `FeaturePolicyTest` and `PackForgeConfigScreenModelTest` passed 19 tests in 11 seconds. Independent review found no blocker. No clean, full build, Minecraft runtime, shader profile, benchmark, or default promotion was run.
- Status: `PHASE_J_ATLAS_RETRY_POLICY_VERIFIED`; atlas retry remains opt-in and Phase J promotion evidence remains incomplete.
- Rollback: after reverting later documentation-only checkpoints, `git revert 943354a4897011b62b4c4cab958afbc92fd16035` restores startup-time config mutation and the prior UI effective-state mapping.

## Default-off candidate dispositions

- Date: 2026-08-13
- Commit SHA: `3168c0f713a475151b57d5ba1b735263a1121c34`
- Parent SHA: `5693964fde112af22ec60ffdd29d50da9bdfd49c`
- Scope: freeze the 11 Phase J candidates, their config keys and parent guards, current configured/effective defaults, implementation reachability, six promotion gates, and one required disposition per candidate.
- Verification: the static validator accepted the catalog and rejected 14 mutations. Independent review confirmed 5 `SAFE_KEEP_DEFAULT_OFF`, 6 `FAILED_WITH_REASON`, 0 promotions, and no unsupported benchmark/runtime claim. CodeGraph found no production consumer for atlas mip scheduling or the model optimizer load methods.
- Status: `PHASE_J_VERIFIED_COMPLETE_CONSERVATIVE`; the disposition gate is complete and no promotion was attempted. Every runtime/performance gate remains `NOT_RUN`; this checkpoint is not benchmark, compatibility-profile, final-JAR, or Minecraft proof.
- Rollback: after reverting later documentation-only checkpoints, `git revert 3168c0f713a475151b57d5ba1b735263a1121c34` removes the frozen disposition catalog and restores the older claims-only matrix.

## Runtime compatibility profile reporting

- Date: 2026-08-13
- Commit SHA: `5ee5f34bc8e56f28f134edab5f311f8797dea557`
- Parent SHA: `3168c0f713a475151b57d5ba1b735263a1121c34`
- Scope: add an environment-gated runtime profile marker that records loader-observed mod presence, exact Quick Pack ownership, and the effective state of all 29 capability paths; normal runs remain dormant.
- Verification: focused Fabric 1.21.1 reporter tests passed three tests in 10 seconds. The parser contract, exact six overlap capabilities, 23 retained capabilities, sorted mod status, and no-request behavior are asserted; independent review passed after those invariants were made exhaustive.
- Status: `PHASE_I_REPORTER_STRUCTURAL_VERIFIED`; no third-party JAR, smoke client, resource fixture, or compatibility profile was executed.
- Rollback: `git revert 5ee5f34bc8e56f28f134edab5f311f8797dea557` before reverting `3168c0f` removes the harness-only runtime marker and core initialization call.

## Compatibility profile metadata pins

- Date: 2026-08-13
- Commit SHA: `2eccf28c7486c037b31465d8330e59a8ea907736`
- Parent SHA: `5ee5f34bc8e56f28f134edab5f311f8797dea557`
- Scope: record exact release URLs, versions, and SHA-256 pins for Quick Pack and ImmediatelyFast on Fabric, Forge, and NeoForge 1.21.1 plus the Fabric combined profile; record public-source unavailability evidence for three Resource Pack Unbounded 1.21.1 cells; replace synthetic markers with the runtime reporter contract.
- Verification: catalog validation passed 36 frozen recipes; the positive `AVAILABLE` control and 19 invalid mutations passed. Independent review confirmed 7 `AVAILABLE`/`UNTESTED`, 26 `PENDING_METADATA`/`UNTESTED`, 3 `UNAVAILABLE`, and no synthetic marker or executed-result claim.
- Status: `PHASE_I_METADATA_PARTIAL_VERIFIED`; metadata availability is not execution. No artifact was downloaded, materialized, launched, or marked PASS, so Phase I remains incomplete.
- Rollback: `git revert 2eccf28c7486c037b31465d8330e59a8ea907736` first, then `5ee5f34`, to restore the all-pending catalog foundation.

## Deterministic compatibility fixtures

- Date: 2026-08-13
- Commit SHA: `458616fb1d3eaa96623ce86dfed41749daf60e38`
- Parent SHA: `d186ab0d1687f7b6cca5f7d11121c77e8028ddca`
- Scope: add a deterministic generator and manifest for the nine required 1.21.1 fixture identities, including normal, high-entry-count, overlay/namespace/duplicate, font-heavy, model-heavy, mipmap-heavy, and malformed-but-ZIP-readable coverage contracts.
- Verification: `python scripts/Generate-CompatibilityFixtures.py --self-test` passed, proving repeat generation yields the same fixture IDs, bytes, CRCs, and contract manifest. Unsupported Minecraft versions are rejected by the generator. No Gradle build, third-party JAR materialization, Minecraft launch, reload, semantic-hash, cancellation, cross-version, or final-JAR proof was run.
- Status: `PHASE_I_K_FIXTURE_INPUTS_STRUCTURAL_VERIFIED`; these are deterministic test inputs and execution scenarios, not compatibility or exact-matrix results.
- Rollback: after reverting `b1161295bd3a41a0349841fd67ff3f0c1634c3bd` and later documentation checkpoints, `git revert 458616fb1d3eaa96623ce86dfed41749daf60e38` removes the fixture generator without changing runtime behavior.

## Source-metrics checkpoint refresh

- Date: 2026-08-13
- Commit SHA: `b1161295bd3a41a0349841fd67ff3f0c1634c3bd`
- Parent SHA: `458616fb1d3eaa96623ce86dfed41749daf60e38`
- Scope: refresh only the checked-in deterministic source-metrics Markdown and JSON snapshot after the compatibility reporter and fixture work.
- Verification: `./gradlew.bat reportSourceMetrics --no-daemon --console=plain` passed in 36 seconds with 210 production Java files, 16,323 nonblank LOC, zero exact and normalized duplicate groups, 15 target-key/version conditional lines, three renderer bodies plus two adapters, and 20 publication artifacts. No compile, package, artifact verification, or Minecraft runtime was run.
- Status: `PHASE_A_METRICS_SNAPSHOT_REFRESHED`; this is a source-inventory checkpoint, not a broader build or release verification.
- Rollback: `git revert b1161295bd3a41a0349841fd67ff3f0c1634c3bd` restores the prior checked-in metric snapshot only.

## Compatibility profile materialization transport

- Date: 2026-08-13
- Commit SHA: `6257c35d50d04e4d874b1175d4808af52fbacdb1`
- Parent SHA: `f15e8575536706570b415a2307dd0d1d76415de3`
- Scope: wire schema-2 compatibility-profile materialization through `Run-Exact-ProductionMatrix.ps1`, all three loader production-smoke wrappers, and `Test-ExactProductionMatrixProfiles.ps1`, including pinned-mod staging, fixture transport, expected markers, and fail-closed override/path handling.
- Verification: the offline focused profile self-test passed in 15.7 seconds; AST validation passed for all five scripts; independent review passed.
- Status: `PHASE_I_MATERIALIZER_STRUCTURAL_VERIFIED`; no network request, artifact download, Gradle task, Minecraft launch, or compatibility runtime was executed. All seven metadata-`AVAILABLE` profiles remain `UNTESTED`; ImmediatelyFast-only profiles fail closed until a dedicated runtime path marker exists, and nonempty configuration overrides fail closed until override transport is implemented.
- Rollback: after reverting later documentation and profile-runner dependants, `git revert 6257c35d50d04e4d874b1175d4808af52fbacdb1` removes schema-2 profile materialization and restores selector-only profile handling.

## Authoritative direct Stonecutter contract

- Date: 2026-08-13
- Commit SHA: `3c01a50`; parent `b7e60da`
- Status: `STRUCTURAL_VERIFIED`; Phase E remains `PARTIAL`.
- Scope: all 53 registry-derived loader distributions are authoritative direct cells; public graph is direct-only; JOptSimple registry metadata remains explicit; `validateStonecutterDirectContract` is mandatory.
- Verification: direct contract AST/self-test passed baseline plus 10 rejected mutations; direct task passed; `validateTargetRegistry` dry-run passed in 31 seconds without compilation; independent review passed.
- Limits: nested Gradle remains only as parity rollback oracle. No all-53 build, broad structural/package parity, Java 17/21/25 proof, remap/JarJar proof, Minecraft runtime, or true Stonecutter-preprocessed source proof occurred.
- Rollback: revert later dependants, then `git revert 3c01a50`.

## Current-evidence documentation reconciliation

- Date: 2026-08-13
- Commit SHA: `d833747749ed665595231b6e72acc22f469b7e1a`
- Parent SHA: `e6246082eda901dc7d202a067bcec432843209bd`
- Scope: reconcile the README and eight Stonecutter-refactor evidence documents with the implemented direct-build, compatibility-profile, default-candidate, manifest, source-inventory, and validation state; remove stale release-ready language and distinguish historical runtime/hash proof from current direct-build bytes.
- Verification: focused documentation consistency inspection and independent documentation review passed. No build, compile, package, final-JAR verification, compatibility-profile execution, or Minecraft runtime was run.
- Status: `PARTIAL_NOT_RELEASE_READY`; this checkpoint corrects evidence claims only. Current direct-build artifacts still require final-JAR rebuild, manifest verification, exact same-artifact runtime coverage, and compatibility-profile execution before release readiness can be claimed.
- Rollback: revert `07b087fa1e3c862f086a25e29bab68eec37477d2` first, then `git revert d833747749ed665595231b6e72acc22f469b7e1a` to restore the prior documentation state.

## Authoritative static implementation-contract gate

- Date: 2026-08-13
- Commit SHA: `07b087fa1e3c862f086a25e29bab68eec37477d2`
- Parent SHA: `d833747749ed665595231b6e72acc22f469b7e1a`
- Scope: aggregate the configuration-screen, compatibility-profile catalog, default-off candidate catalog, and deterministic fixture validators under `validateImplementationContracts`; require that gate from registry validation and CI; verify an existing generated release manifest before publication upload.
- Verification: `validateImplementationContracts` passed all 4/4 contracts in 36.9 seconds without compilation; the focused Python release-manifest test passed; independent review passed. A YAML parser was unavailable, so workflow YAML received focused static inspection only.
- Status: `STATIC_CONTRACTS_VERIFIED`; no compilation, package build, final-JAR manifest verification, compatibility-profile execution, or Minecraft runtime was run. This is not release-ready evidence.
- Rollback: `git revert 07b087fa1e3c862f086a25e29bab68eec37477d2` removes the aggregate static gate and publication verify-existing step while retaining the reconciled evidence documentation.

## Expanded exact compatibility-profile pins

- Date: 2026-08-13
- Commit SHA: `a80a83e77ed8190ad4de1e0c07665264af48f1c9`
- Parent SHA: `293e7aa`
- Scope: expand the exact 1.21.1 catalog to 21 `AVAILABLE`/`UNTESTED`, 12 `PENDING_METADATA`/`UNTESTED`, and three `UNAVAILABLE`; freeze exact loader floors, dependencies, runtime IDs, URLs, versions, and hashes. Fabric Quick Pack moved to pending because Loader `0.15.11` is below its public artifact floor of `>=0.17.3`.
- Verification: normal catalog validation and self-test passed all 36 recipes and 19 rejected mutations. Twenty unique new pins were independently verified against SHA-256 and embedded loader metadata; verification downloads were not retained.
- Status: `PHASE_I_METADATA_EXPANDED_RUNTIME_UNTESTED`; no Minecraft client, compatibility runtime, or PASS profile was produced. The repository remains `PARTIAL_NOT_RELEASE_READY`.
- Rollback: revert the documentation checkpoint first, then `git revert a80a83e77ed8190ad4de1e0c07665264af48f1c9` to restore the seven-available catalog.

## Schema-2 compatibility-profile configuration contract

- Date: 2026-08-13
- Commit SHA: `eb14498`; parent `a15c4e0`
- Scope: add one shared, full `PackForgeConfig.Cfg` v12 (50 serialized fields) smoke baseline to schema-2 profile materialization and all loader wrappers. Exactly five safe boolean override keys are accepted: ZIP read pool, font bitmap cache, atlas decode batching, atlas retry, and startup executor tuning. No production default or default-off candidate is promoted.
- Verification: seven-script AST validation passed; normal catalog validation accepted all 36 frozen recipes; self-test rejected 22 catalog mutations plus Java default drift; offline Fabric/Forge/NeoForge schema-2 transport passed in 22.1 seconds; independent review passed.
- Status: `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED`. The baseline is checked against every Java initializer, permits only the documented smoke-observability deltas, and is JSON/SHA-256 stable.
- Limits: no Gradle task, network request, artifact download, Minecraft launch, runtime profile, reload, or release proof was run.
- Rollback: newest-first—revert later dependent checkpoints first, then `git revert eb14498` to remove full configuration transport and return nonempty profile overrides to their prior fail-closed behavior.

## Phase G — exact shared configuration-screen routes

- Date: 2026-08-13
- Commit SHA: `5d8deab`; parent `ebdb90d`
- Status: `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED`.
- Scope: bind the exact shared configuration-screen base/wrapper contract and PackSelection entry routes so each supported version-family route resolves to the canonical shared implementation.
- Verification: AST validation, normal contract validation, focused self-test with mutation rejection, and independent review passed.
- Limits: no Gradle task, build, Minecraft launch, live UI/runtime, artifact verification, or release proof was run.
- Rollback: revert later dependants first, then `git revert 5d8deab` to restore the preceding screen-route contract.

## Phase K — hash-bound resumable exact-production PASS evidence

- Date: 2026-08-13
- Commit SHA: `aa452ac`; parent `5d8deab`
- Status: `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED`.
- Scope: bind resumable exact-production PASS records to the current cell/release/loader/target/coordinate/artifact/fixture/fingerprint, exact production PASS-line tokens, and SHA-256-verified log/provenance evidence; precompute matrix evidence and scan prior records once; fail safely on numeric overflow or evidence I/O; make cumulative evidence failure fail the controller exit.
- Verification: PowerShell AST validation; data-only self-test accepted a valid controlled graceful exit and rejected 17 mutations, including malformed numeric/hash/evidence cases; valid-then-later-invalid chronology and final-summary failure behavior passed; independent review passed.
- Limits: no Gradle task, Minecraft launch, network request, or matrix execution was run.
- Rollback: revert later dependants first, then `git revert aa452ac` to restore the preceding resumable-PASS handling.

## Phase E — first native Stonecutter capability pilot

- Date: 2026-08-13
- Commit SHA: `b54a40f`; parent `b66a0cc`
- Status: `PHASE_E_PARTIAL`.
- Scope: make Fabric archive capture the first native Stonecutter capability pilot: canonical source for 16 active targets from 1.20.2 through 1.21.10, with the source-family seam at 1.20.5. Standalone, Forge, NeoForge, and legacy rollback paths remain preserved.
- Verification: direct-contract self-test passed for 53 cells, 16 native targets, and 21 rejected mutations. Offline boundary compile for 1.20.4 and 1.20.5 passed in 37 seconds (launcher timeout left daemon output); generated handlers/classes were distinct and correct. `mc1_20_5` `sourcesJar` passed in 25 seconds; fresh `devlibs` contained 67 files and 59 Java sources, exactly one canonical archive-capture entry with SHA-256 prefix `2EB1`, matching generated output, and no wrong root or legacy source. Independent review passed.
- Limits: no runtime, full build, full matrix, artifact release verification, or Minecraft acceptance was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert b54a40f` to restore the preceding delegated/canonical source selection while preserving standalone, Forge, NeoForge, and legacy rollback paths.

## Phase E — native loader-source metrics and compact archive-capture boundary

- Date: 2026-08-13
- Commit SHA: `4f82154`; parent `b54a40f`
- Status: `PHASE_E_PARTIAL`.
- Scope: include handwritten root loader branches in source metrics, make the Stonecutter conditional parser fail closed for the active directive forms, and compact the canonical archive-capture path through a helper while retaining the native Fabric capability pilot.
- Verification: direct-contract self-test passed 22 mutations. `reportSourceMetrics updateSourceMetricsCheckpoint` passed in 34 seconds: 211 production files, 16,366 LOC, 104 bridge files / 5,346 LOC (32.67%), zero exact/normalized duplicate groups, three Stonecutter conditional blocks with maximum 39 lines. Offline boundary compile passed in 30 seconds; `sourcesJar` passed in 24 seconds with fresh `devlibs` at 67 files / 59 Java sources and canonical SHA-256 `BEAFDA...` matching generated output. Independent review passed.
- Limits: no runtime, full build, full matrix, release verification, or Minecraft acceptance was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert 4f82154`; next revert `b54a40f` if the native Fabric archive-capture pilot must also be removed.

## Phase E — canonical preprocessed SharedZip Fabric seam

- Date: 2026-08-13
- Commit SHA: `7efd43f`; parent `deaf765`
- Status: `PHASE_E_PARTIAL`.
- Scope: canonical preprocessed Fabric `SharedZipFileAccessMixin` for 17 active targets from 1.20.2 through 1.21.11, normalized exact parity with the retained standalone shared source, registry-derived activation without a new `target.key` branch, and retained mc26 physical accessor/invoker seam. Minecraft 1.20.1 has no native SharedZip activation.
- Verification: direct-contract self-test passed 38 mutations. Source metrics passed in 26 seconds: 212 production files, 16,403 LOC, 105 bridge files / 5,383 LOC (32.82%), zero duplicate groups, four Stonecutter blocks with maximum 40 lines, and 18 platform `target.key` references. Focused combined compile/sourceJar passed in 31 seconds across 14 tasks; follow-up sourceJar was UP-TO-DATE PASS in 24 seconds. Exact generated checksums matched: active SharedZip `C4694D...`, mc26 mixin `1D281C...`, and mc26 accessor `F7131E...`.
- Limits: no Minecraft runtime, full build, full matrix, release verification, or cross-loader native preprocessing proof was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert 7efd43f`; then revert `4f82154` and `b54a40f` in order if removing the preceding Fabric native pilot work.

## Phase E — canonical preprocessed Bitmap provider Fabric client seam

- Date: 2026-08-13
- Commit SHA: `c5b61e2`; parent `c9349ea`
- Status: `PHASE_E_PARTIAL`.
- Scope: canonical preprocessed Fabric client `BitmapProviderDefinitionMixin`, guarded from 1.20.1 through 26.1 across all 19 targets. Three physical variants remain for standalone and other loaders; direct builds transport generated `client/java` sources. Activation remains registry-derived with no new `target.key` branch.
- Verification: direct-contract self-test passed 49 mutations. Source metrics passed in 32 seconds: 213 production files, 16,430 LOC, 106 bridge files / 5,410 LOC (32.93%), zero duplicate groups, five Stonecutter blocks with maximum 40 lines, and 18 platform `target.key` references. Focused Fabric 1.20.1/1.21.11/mc26 `compileClientJava` plus `sourcesJar` passed in 32 seconds across 24 tasks (16 executed, eight up-to-date). Each target produced one compiled Bitmap class of 2,655 bytes and one source-JAR entry whose SHA-256 exactly matched its generated source: `35EF7378...CB22`.
- Limits: no Minecraft runtime, full build, full matrix, release verification, or cross-loader native preprocessing proof was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert c5b61e2`; then revert `7efd43f`, `4f82154`, and `b54a40f` in order if removing the preceding Fabric native pilot work.

## Phase E — canonical preprocessed ReloadableResourceManager Fabric pre26 seam

- Date: 2026-08-13
- Commit SHA: `aba98b3`; parent `4846e15`
- Status: `PHASE_E_PARTIAL`.
- Scope: canonical guarded Fabric `ReloadableResourceManager` preprocessing for the exact 18 pre26 targets from 1.20.1 through 1.21.11, with generated `main` transport. The mc26 physical StartupStatus/StartupTimings seam remains retained.
- Verification: direct-contract static self-test passed 61 rejected mutations: 53 total cells, 16 archive targets, 17 SharedZip targets, 18 ReloadableResourceManager targets, and 19 Bitmap targets. Source metrics passed in 36 seconds: 214 production files, 16,471 LOC, 107 bridge files / 5,451 LOC (33.09%), zero duplicate groups, six Stonecutter blocks with maximum 40 lines, and 18 platform `target.key` references. Focused offline Fabric 1.20.1/1.21.11/mc26 `compileJava` plus `sourcesJar` reached BUILD SUCCESSFUL in 35 seconds across 18 tasks (12 executed, six up-to-date). Active entry SHA-256 `ACD7634C...DDFD` matched generated output; mc26 SHA-256 `1D05F03C...A13C` matched the retained physical source; classes existed in all three cells. Independent final review passed.
- Limits: no Minecraft runtime, all-53 build/package parity, full matrix, release verification, or general cross-loader preprocessing proof was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert aba98b3`; then revert `c5b61e2`, `7efd43f`, `4f82154`, and `b54a40f` in order if removing the preceding Fabric native pilot work.

## Phase E — canonical preprocessed RuntimeResourceHash Fabric seam

- Date: 2026-08-13
- Commit SHA: `f74e0a7`; parent `ea10f52`
- Status: `PHASE_E_PARTIAL`.
- Scope: canonical guarded Fabric RuntimeResourceHash preprocessing for exact 1.20.1 through 1.21.10 (17 targets). The 1.21.11 raw Predicate/String and mc26 Identifier physical seams remain; standalone, Forge, and NeoForge paths are untouched.
- Verification: static direct-contract self-test passed 73 rejected mutations and includes RuntimeResourceHash for 17 targets. Source metrics passed in 27 seconds: 215 production files, 16,496 LOC, 108 bridge files / 5,476 LOC (33.20%), zero duplicate groups, seven Stonecutter blocks with maximum 40 lines, and 18 platform `target.key` references. Focused offline Fabric 1.20.1/1.21.10/1.21.11/mc26 `compileJava` plus `sourcesJar` reached BUILD SUCCESSFUL in 44 seconds across 24 tasks (18 executed, six up-to-date). Active SHA-256 `F1726800...D9B7` matched generated output; physical 1.21.11 `ED80675E...AE83` and mc26 `532500D6...ABE6` matched; inactive generated output was absent; classes existed. Independent final review passed.
- Limits: no Minecraft runtime, all-53 build/package parity, full matrix, release verification, or general cross-loader preprocessing proof was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert f74e0a7`; then revert `aba98b3`, `c5b61e2`, `7efd43f`, `4f82154`, and `b54a40f` in order if removing the preceding Fabric native pilot work.

## Phase E — canonical preprocessed LoadingOverlayToast Fabric seam

- Date: 2026-08-13
- Commit SHA: `731d1c2`; parent `9caa86e`
- Status: `PHASE_E_PARTIAL`.
- Scope: canonical Fabric LoadingOverlayToast preprocessing for 14 active targets: common guard 1.20.5 through 1.21.5 (eight targets; 18 lines) and modern guard 1.21.6 through 1.21.11 (six targets; 31 lines). Physical 1.20.1-1.20.4 adapter and mc26 priority-1100 tick-at-HEAD seam remain; standalone, Forge, and NeoForge are untouched.
- Verification: static direct-contract self-test passed 89 rejected mutations and includes LoadingOverlayToast for 14 targets. Source metrics passed in 27 seconds: 216 production files, 16,544 LOC, 109 bridge files / 5,524 LOC (33.39%), zero duplicate groups, nine Stonecutter blocks with maximum 40 lines, and 18 platform `target.key` references. Focused offline six-boundary Fabric `compileClientJava` plus `sourcesJar` reached BUILD SUCCESSFUL in 52 seconds across 48 tasks (39 executed, nine up-to-date). Physical lower SHA-256 `6405DCD1...`, generated common `734966D6...`, generated modern `12A3E4CA...`, and physical mc26 `8D9A1069...` matched; classes existed. Independent final review passed.
- Limits: no Minecraft runtime, all-53 build/package parity, full matrix, release verification, or general cross-loader preprocessing proof was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert 731d1c2`; then revert `f74e0a7`, `aba98b3`, `c5b61e2`, `7efd43f`, `4f82154`, and `b54a40f` in order if removing the preceding Fabric native pilot work.

## Phase E — canonical preprocessed SimpleReload Fabric seam

- Date: 2026-08-13
- Commit SHA: `b1ee885`; parent `ebb7c41`
- Status: `PHASE_E_PARTIAL`.
- Scope: exact active Fabric SimpleReload coverage for `mc1_21_6`, `mc1_21_7`, and `mc1_21_8` through adapter plus source policy. Adjacent and all other eras retain physical sources.
- Verification: static self-test passed 106 mutations. Source metrics passed in 30 seconds: 217 production files, 16,575 LOC, 110 bridge files / 5,555 LOC (33.51%), zero duplicate groups, ten Stonecutter blocks with maximum 40 lines, and 18 platform `target.key` references. Focused 1.21.5/1.21.6/1.21.8/1.21.9 compile/sourceJar reached BUILD SUCCESSFUL in 46 seconds across 24 tasks (20 executed, four up-to-date). Active source SHA-256 `C30B2016...` matched; inactive physical SHA prefixes `BB0AA935...` and `E94AAB4F...` remained; classes existed. Independent final review passed.
- Limits: no Minecraft runtime, all-53 build/package parity, full matrix, release verification, or general cross-loader preprocessing proof was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert b1ee885`; then revert `731d1c2`, `f74e0a7`, `aba98b3`, `c5b61e2`, `7efd43f`, `4f82154`, and `b54a40f` in order if removing the preceding Fabric native pilot work.

## Phase E — extend canonical SimpleReload preprocessing to 1.21.5

- Date: 2026-08-15
- Commit SHA: `0138d6edbad4cebd45773050d81e7897dbbfb7b3`; parent `9ab73924b87b025553a871968c62d2b1a613e07a`
- Status: `PHASE_E_PARTIAL`.
- Scope: extend exact active Fabric SimpleReload preprocessing from 1.21.6-1.21.8 to 1.21.5-1.21.8. The 1.21.5 adapter and mixin descriptor remain version-specific; pre-1.21.5 constructor and 1.21.9+ SharedState seams remain physical.
- Verification: `validateStonecutterDirectContract` passed in 47 seconds across 53 direct cells with four native SimpleReload cells. Focused offline Fabric 1.21.4/1.21.5/1.21.6 `compileJava` plus `sourcesJar` reached BUILD SUCCESSFUL in 51 seconds across 18 executed tasks. The 1.21.5 and 1.21.6 generated/source-JAR entries matched SHA-256 prefix `B2F6321B` and produced 4,648-byte classes; 1.21.4 kept the generated source inactive, retained its 4,641-byte physical class, and produced no generated source-JAR entry. Source metrics passed in 59 seconds with 217 production files, 16,575 LOC, zero duplicate groups, ten Stonecutter blocks with maximum 40 lines, and 103 validator target-key literals. Independent read-only review passed with no required fixes.
- Limits: no clean build, Minecraft runtime, all-53 build/package parity, full matrix, release verification, or general cross-loader preprocessing proof was run. Phase E remains `PARTIAL`.
- Rollback: revert later dependants first, then `git revert 0138d6e`; then revert `b1ee885`, `731d1c2`, `f74e0a7`, `aba98b3`, `c5b61e2`, `7efd43f`, `4f82154`, and `b54a40f` in order if removing the preceding Fabric native work.

## Whole-plan A-L current-state audit

- Date: 2026-08-15
- Commit SHA: `1d03c6dcd4bd010bde9ccd9d2d326e8f95ef7a97`; parent `42cfd18342abad235a95650bdb74869fb23801f5`
- Status: `PARTIAL_NOT_RELEASE_READY`.
- Scope: re-audit every phase in `PackForge_Missing_Implementation_Plan.md`, replace noncanonical status labels with the assignment's five statuses, separate source from test/artifact evidence, record contradictions, and dependency-order remaining work.
- Verification: all 12 phase rows and the required five-column schema passed focused structural checks; independent review failed the first calibration, then passed after C/G/H status repair, contradiction links, and artifact-count target correction. Current artifact inventory is 20 expected / 17 present / five matching / 15 missing / 12 stale; `verifyExistingArtifacts` failed on missing version-selection mixin plugins and no manifest exists.
- Limits: no source behavior changed. No clean build, all-53 compilation/package run, Minecraft runtime, profile, benchmark, or release publication belongs to this checkpoint.
- Rollback: revert later documentation dependants first, then `git revert 1d03c6d` to restore the preceding Phase E-focused audit.

## Whole-plan report reconciliation

- Date: 2026-08-15
- Commit SHA: `90ed4eababa35aecb151d2b5eeca33705c7abfeb`; parent `1d03c6dcd4bd010bde9ccd9d2d326e8f95ef7a97`
- Status: `PARTIAL_NOT_RELEASE_READY`.
- Scope: reconcile `compatibility-matrix.md`, `implementation-report.md`, `quick-pack-compatibility.md`, `rollback.md`, `stable-checkpoints.md`, and `work-log.md` with the canonical A-L audit. Correct the six Quick Pack profile count, current native-preprocessing coverage, live artifact conflict, and historical/current checkpoint wording.
- Verification: focused documentation review passed after clarifying that `71f29b` is the initial historical continuation baseline rather than a current verified checkpoint.
- Limits: documentation only. No build, package, runtime cell, compatibility profile, benchmark, or publication belongs to this checkpoint.
- Rollback: revert later documentation dependants first, then `git revert 90ed4ea`; revert `1d03c6d` afterward only if also removing the canonical whole-plan audit.

## Phase G — replacement-stage configuration safety

- Date: 2026-08-15
- Commit SHA: `c696253832a005ffbbccf838e52a639ba1658954`; parent `90ed4eababa35aecb151d2b5eeca33705c7abfeb`
- Status: `PHASE_G_IMPLEMENTED_UNVERIFIED`.
- Scope: add a focused failure-path test that reaches configuration replacement after the temporary file is written, using an existing nonempty directory as the blocked `packforge.json` target. The test requires the installed live snapshot and target sentinel to survive and the temporary file to be removed.
- Verification: offline Fabric 1.21.1 `PackForgeConfigPreservationTest` reached BUILD SUCCESSFUL in 54 seconds across ten executed tasks. Independent 5.6-sol-wm review passed the exact diff with no required fixes.
- Limits: no production source changed. No clean build, live UI route, renderer-family client run, shader/atlas profile, full matrix, or release verification belongs to this checkpoint. Phase G remains `IMPLEMENTED_UNVERIFIED`.
- Rollback: revert later documentation dependants first, then `git revert c696253` to remove only the replacement-stage test.

## Phase K — resolved-resource hash binding

- Date: 2026-08-15
- Commit SHA: `6ac88581dbd4b1fd938a84396bb17e96a964d4c3`; parent `86dfcd77a5b571e49f796e49039984a206d1eacd`
- Status: `PHASE_K_PARTIAL`.
- Scope: require positive deterministic resolved-resource hashes in controlled Fabric/Forge/NeoForge production smokes; stage immutable deterministic fixtures for base Fabric cells; bind the normalized hash into exact-matrix PASS lines, records, resume validation, and harness fingerprints; add the focused Gradle implementation-contract gate.
- Verification: helper, matrix resume, profile transport, PowerShell parse, focused `validateRuntimeResourceHashEvidence`, and aggregate `validateImplementationContracts` checks passed. No Minecraft launch, dependency download, final-JAR runtime, 62-cell matrix, or release verification was run.
- Limits: this proves the acceptance contract and fixture transport only. Current artifacts remain invalid/stale and no semantic hash has been observed from current final JARs; Phase K remains `PARTIAL`.
- Rollback: revert later documentation dependants first, then `git revert 6ac8858`; the prior matrix remains structurally usable but without the resolved-resource hash contract.

## Phase I/K — hash-visible compatibility fixtures

- Date: 2026-08-15
- Commit SHA: `c610fc1936bbff61aca6ae6cbd48490f05a63a29`; parent `4d4596acdc95985b35d5d8466c0aa5ea83a03858`
- Status: `PHASE_I_K_FIXTURE_INPUTS_STRUCTURAL_VERIFIED`.
- Scope: add one deterministic `assets/example/textures/fixture-marker.txt` resource to every compatibility fixture, update manifest entry counts/required entries, and make the ZIP contract assert its presence. This aligns all nine Phase I fixture families with the Phase K positive-entry semantic-hash gate without broadening the runtime namespace filter.
- Verification: `python scripts/Generate-CompatibilityFixtures.py --self-test` passed; aggregate `validateImplementationContracts --offline --no-daemon --console=plain --stacktrace` passed (five actionable tasks).
- Limits: no production Java, Minecraft launch, dependency download, final-artifact runtime, 62-cell matrix, cancellation, profile, or release verification was run; the marker proves input shape only, not a runtime hash.
- Rollback: revert later documentation dependants first, then `git revert c610fc1`; the prior fixture catalog remains deterministic but most families no longer satisfy the positive-entry runtime-hash precondition.

## Phase K — ten-reload lifecycle gate

- Date: 2026-08-15
- Commit SHA: `55b5ac4815d4d90f6092f2a22f36af43b4ebbbbe`; parent `6a04e3fbc65fb265c599f0a66a9890997f03a734`
- Status: `PHASE_K_PARTIAL`.
- Scope: make the exact production matrix require ten reloads per cell, align Fabric/Forge/NeoForge production-smoke defaults to ten, and extend the focused hash-contract self-test to protect the default.
- Verification: PowerShell parse checks, runtime-resource-hash self-test, exact-matrix resume self-test with 21 invalid mutations, three-loader profile transport self-test, and aggregate `validateImplementationContracts` passed.
- Limits: no production Java, Minecraft launch, dependency download, final-artifact runtime, 62-cell matrix, cancellation, profile, or release verification was run; the gate enforces the planned count but does not prove reload stability.
- Rollback: revert later documentation dependants first, then `git revert 55b5ac4`; prior matrix logic remains available with the earlier two-reload default.

## A/E/F/I/K/L — implemented-unverified structural continuation

- Date: 2026-08-15
- Implementation commits: `abdf2fe` (direct Stonecutter/parity split and Phase F ledger), `f5a76d7` (Phase I dispositions and Phase K fixture/scenario/release-manifest evidence binding), `b53cd5f` (current-status documentation reconciliation).
- Parent chain: `0b95eb6` → `abdf2fe` → `f5a76d7` → `b53cd5f`.
- Status: A, E, F, I, K, and L are `IMPLEMENTED_UNVERIFIED`; overall branch remains `PARTIAL_NOT_RELEASE_READY`.
- Verification: consolidated PowerShell parse/self-tests, direct-contract self-test (53 cells, 106 mutations), compatibility catalog self-test (36 profiles), runtime-resource-hash self-test, exact-matrix resume self-test (22 mutations), profile transport self-test, deterministic fixture/release-manifest tests, `git diff --check`, and offline Gradle `validateTargetRegistry validateImplementationContracts validateStonecutterDirectContract reportSourceMetrics validatePhaseFSourceReduction` passed. Phase F reports `FAIL` for its known cleanup gates with status `IMPLEMENTED_UNVERIFIED` and a 759-LOC shortfall.
- Limits: no clean/full build, all-53 package parity, current 20-JAR manifest, final-JAR runtime, 62-cell matrix, compatibility launch, benchmark, cancellation execution, or release publication was run. Existing artifacts remain stale/incomplete.
- Rollback: revert later documentation dependants first, then `git revert b53cd5f`; revert `f5a76d7` and `abdf2fe` in reverse order if removing the implementation units.
