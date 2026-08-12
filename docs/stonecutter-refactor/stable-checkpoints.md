# Stable checkpoints

Checkpoint entries are chronological and must include commit SHA, parent, scope, exact verification, result, and rollback instruction.

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
