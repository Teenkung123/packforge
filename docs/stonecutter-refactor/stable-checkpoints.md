# Stable checkpoints

Checkpoint entries are chronological and must include commit SHA, parent, scope, exact verification, result, and rollback instruction.

## Baseline documentation

- Commit SHA: to be recorded immediately after the Phase 0 documentation commit
- Parent SHA: `609270f533666e7636d11f7d16590be925ec836f`
- Scope: baseline evidence and migration rollback documents only
- Verification: `validateTargetRegistry`, clean `buildAllSupported`, direct `verifyExistingArtifacts`, focused tests, PackIndex benchmark; runtime smoke limitations recorded
- Status: `FOCUSED_VERIFIED`
- Rollback: no functional behavior changed; revert the baseline documentation commit if necessary
