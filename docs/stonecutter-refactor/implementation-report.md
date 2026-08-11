# Implementation report

This file starts as the baseline report and is extended after each verified phase. Executed baseline evidence remains in `baseline.md`, `artifact-consolidation.md`, and `compatibility-matrix.md`.

Current status: Phase 0, registry schema v2, and a bounded Stonecutter current-target pilot are complete on `codex/packforge-stonecutter-refactor`. The current six-target build remains authoritative until full Stonecutter parity passes. Latest pre-registry source/build baseline is `609270f533666e7636d11f7d16590be925ec836f`.

Phase 1 evidence: `validateTargetRegistry` and `printResolvedMatrix` pass with 22 exact release cells; a focused all-loader `mc1_21_1` build and artifact verification pass. The first focused attempt found and repaired the child-build schema guard; no functional artifact change was intended.

Phase 2 evidence: Stonecutter 0.9.7 generates `:mc1_21_1`, and `verifyStonecutterCurrentTarget` builds the three standalone loader projects and checks the exact current artifact set. The first delegation was intentionally rejected as a recursive task graph and replaced with direct platform invocations; the legacy root aggregator remains unchanged.

Phase 5 evidence: `FeaturePolicy` now owns capability/config gating and copies the live config at a policy boundary. `ReloadFeatureSnapshot.capture()` uses one policy instance, while the unchanged `FeatureFlags` facade preserves existing callers. The focused Fabric suite passes with 106 tests.

Open mandatory work: full Stonecutter parity, central effective ownership policy, capability deduplication, unified screens, Quick Pack profiles, 22 exact releases, artifact proof/consolidation, candidate evaluation, CI/publication generation, and final runtime evidence.
