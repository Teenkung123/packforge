# Stonecutter refactor work log

## Phase 0 — baseline and branch safety

- Date: 2026-08-11
- Commit SHA: `71b6ba396b60b79ed0cf2bdfb8c31ab7f7c76ce0`
- Parent stable checkpoint: `609270f533666e7636d11f7d16590be925ec836f`
- Status: `FOCUSED_VERIFIED`
- Files changed: `docs/stonecutter-refactor/*.md`
- Architecture decision: use a staged strangler migration; preserve standalone loader builds until a one-target Stonecutter parity pilot passes.
- Commands: see `baseline.md`.
- Results: registry validation PASS; clean 17-artifact build PASS; direct final-artifact verification PASS; Fabric/Forge/NeoForge focused tests PASS; PackIndex benchmark PASS with equal semantic hashes; aggregate `verifyAllArtifacts` timed out and is not claimed; runtime harnesses reached startup/reload but stopped at GUI window activation.
- Artifacts: 17 baseline JARs, 23,691,877 total bytes; SHA-256 list in `artifact-consolidation.md`.
- Semantic hash: `28ba0ce3fce57d83e14e7e9d47c386316eba77156baaa3ac5984fe47952f4a8b` for baseline and indexed paths.
- Compatibility: exact current matrix build/test evidence only; runtime and third-party profiles remain `UNTESTED` where stated.
- Source metrics: 16,837 production LOC / 212 Java files; 20 normalized duplicate-file groups across versions.
- Rollback point: baseline HEAD `609270f533666e7636d11f7d16590be925ec836f`.
- Next mandatory phase: registry schema v2 without artifact behavior change.
