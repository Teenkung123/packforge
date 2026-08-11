# Rollback plan

## Baseline

The functional baseline is commit `609270f533666e7636d11f7d16590be925ec836f`. The implementation branch is `codex/packforge-stonecutter-refactor`; the unrelated modified `.github/ISSUE_TEMPLATE/bug-report.yml` must remain outside functional commits.

## Rules

- Use the chronological map in `stable-checkpoints.md`.
- Revert verified functional units with normal `git revert <sha>`.
- Do not reset, discard, amend, squash, rebase away, or force-push verified checkpoints.
- Keep legacy build tasks available until Stonecutter current-matrix parity is proven.
- Keep per-target capability exclusions and per-feature defaults as rollback controls.
- If an optional path fails, return to vanilla/original control flow without disabling unrelated capabilities.

## Migration rollback points

1. Before registry schema migration: baseline HEAD above.
2. Before Stonecutter switch: last verified parallel-build checkpoint.
3. Before each capability migration: immediately preceding checkpoint.
4. Before each range consolidation/default promotion: preceding exact-release proof checkpoint.
5. Final rollback: revert the latest named checkpoint(s) in reverse dependency order.

## Latest checkpoints

- `0dc06bc2a6e684e7efd3d1df5f3f0f1aba6a7db0`: planned 1.20.2 target, Fabric/Forge bridge, Stonecutter node, and planned-target exclusion from the published 17-artifact aggregate. Revert after `862022c` if the feasibility cell is abandoned.
- `862022c40249155f1d43fb55d97f3c4cb132c136`: keep the 1.21.11 archive constructor hook version-local. Revert this first to restore the prior descriptor before reverting the 1.20.2 bridge.

The unrelated `.github/ISSUE_TEMPLATE/bug-report.yml` edit remains unstaged and is not part of any rollback.
