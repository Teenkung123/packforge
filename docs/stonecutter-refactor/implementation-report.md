# Implementation report

This file starts as the baseline report and is extended after each verified phase. Executed baseline evidence remains in `baseline.md`, `artifact-consolidation.md`, and `compatibility-matrix.md`.

Current status: Phase 0, registry schema v2, a bounded Stonecutter current-target pilot, central feature policy, archive/reload source deduplication, and the shared 1.21 client bootstrap are complete on `codex/packforge-stonecutter-refactor`. The current six-target build remains authoritative for the full matrix until Stonecutter parity passes. Latest pre-registry source/build baseline is `609270f533666e7636d11f7d16590be925ec836f`.

Phase 1 evidence: `validateTargetRegistry` and `printResolvedMatrix` pass with 22 exact release cells; a focused all-loader `mc1_21_1` build and artifact verification pass. The first focused attempt found and repaired the child-build schema guard; no functional artifact change was intended.

Phase 2 evidence: Stonecutter 0.9.7 generates `:mc1_21_1`, and `verifyStonecutterCurrentTarget` builds the three standalone loader projects and checks the exact current artifact set. The first delegation was intentionally rejected as a recursive task graph and replaced with direct platform invocations. Follow-up commit `80a35d7bff2a186bf62a3696dc54dcf2dc639e14` restores the repository-level registry/release tasks on the Stonecutter root and wires the pilot verifier to the registry guard.

Phase 5 evidence: `FeaturePolicy` now owns capability/config gating and copies the live config at a policy boundary. `ReloadFeatureSnapshot.capture()` uses one policy instance, while the unchanged `FeatureFlags` facade preserves existing callers. The focused Fabric suite passes with 106 tests.

Phase 6 evidence: the identical 1.21.1/1.21.4/1.21.8 `FilePackResourcesMixin`, `SharedZipFileAccessMixin`, and `ReloadableResourceManagerMixin` implementations now live in `versions/mc1_21_shared/common/src/main/java`. Fabric, Forge, and NeoForge source sets include that root for the three proven targets, while the registry verifier resolves effective ownership and keeps 1.21.11 version-local. Full three-platform target builds and artifact verification pass for all three affected targets.

Phase 6b evidence: the exact duplicate 1.21.1/1.21.4/1.21.8 `PackForgeClient` reset bootstrap now lives in the shared 1.21 client root. The registry verifier enforces one effective owner per target, and all nine affected loader/target builds pass.

Open mandatory work: full Stonecutter parity, remaining capability-specific source deduplication, unified screens, Quick Pack profiles, 22 exact releases, artifact proof/consolidation, candidate evaluation, CI/publication generation, and final runtime evidence.

## Current checkpoint: registry, Quick Pack seams, and the 1.20.2 feasibility cell

The current branch now has a schema-v2 registry with 22 checked-in exact release rows and twelve source families. The registry distinguishes planned cells from the six published anchors; planned cells do not enter `buildAllSupported` or the 17-artifact final verifier. Stonecutter generates the current anchors plus a focused `mc1_20_2` project.

The unified configuration schema/effective-state model and native screen adapters are committed. Quick Pack detection is clean-room metadata detection only, with strict ownership of overlapping index, ZIP pool, font preselection, atlas-mip, loading-fade, and status-overlay paths. Unknown or unreadable Quick Pack versions fail closed. Combined Quick Pack runtime acceptance remains `UNTESTED`.

The 1.20.2 source-family feasibility cell is intentionally still `planned`: Fabric and Forge production builds pass with a thin constructor/sprite/UI bridge, and their focused JARs contain the target descriptor and bridge. NeoForge 20.2 cannot build in this Gradle 9.5.1 workspace: ModDev 2.0.141/20.2.93 has no `neoforge-moddev-bundle` capability, while the official NeoGradle 7.0.116 second attempt fails during Groovy script compilation with `AbstractMethodError`. No 1.20.2 artifact is published or counted in the 17-artifact set.

Final current-matrix evidence at checkpoint `862022c40249155f1d43fb55d97f3c4cb132c136`: `buildAllSupported --rerun-tasks --no-daemon --stacktrace` PASS in 5m31s; `verifyAllArtifacts --no-daemon --stacktrace` PASS in 5m18s; 17 artifacts collected and structurally verified. These are build/package results, not production startup or resource-reload acceptance. The remaining exact-release, Quick Pack combined-runtime, and candidate-promotion cells are explicitly unexecuted.

## Exact 1.20.3/1.20.4 focused evidence

Checkpoint `948e437` adds separate exact `mc1_20_3` and `mc1_20_4` targets under the shared `mc1_20_3_4` source family. `validateTargetRegistry` passes. The exact 1.20.4 Fabric, Forge, and NeoForge builds pass and `verifyMc1_20_4Artifacts` passes. The exact 1.20.3 Fabric and Forge builds pass; NeoForge 20.3.8-beta fails before source compilation because the official artifact does not expose the `neoforge-moddev-bundle` capability required by ModDev 2.0.141. These are build/package results only: no exact 1.20.3/1.20.4 startup, deterministic reload, semantic hash, Quick Pack, or clean-exit claim is made, and neither exact cell enters publication.

## Exact 1.20.5/1.20.6 focused evidence

Checkpoint `21524f3` adds exact `mc1_20_5` and `mc1_20_6` targets under the shared `mc1_20_5_6` Java21 source family. `validateTargetRegistry` passes. The 1.20.5 Fabric build and artifact verifier pass. The 1.20.6 Fabric, Forge, and NeoForge builds and artifact verifier pass. The family reuses the proven 1.21 shared archive/reload/client implementations and keeps only the 1.20.x `ResourceLocation` and Fabric test constructor helpers version-local. The final JARs contain the family client mixin and archive/reload hooks; loader metadata and the access widener are present where applicable. These are build/package results only: no exact 1.20.5/1.20.6 startup, deterministic reload, semantic hash, Quick Pack, or clean-exit claim is made, and neither exact cell enters publication.

## Exact 1.21/1.21.2/1.21.3 focused evidence

Checkpoint `71de8fe` adds exact `mc1_21`, `mc1_21_2`, and `mc1_21_3` targets while retaining the existing adapter families. `validateTargetRegistry` passes. The exact 1.21 and 1.21.3 Fabric, Forge, and NeoForge builds and artifact verifiers pass. The exact 1.21.2 Fabric and NeoForge builds and artifact verifier pass; Forge is not declared because no official Forge 1.21.2 line is available. The eight final JARs package the shared archive/reload hooks, client bootstrap, `PackSelectionScreenMixin`, and the correct loader metadata. These are build/package results only: no exact 1.21/1.21.2/1.21.3 startup, deterministic reload, semantic hash, Quick Pack, or clean-exit claim is made, and no exact cell enters publication.
