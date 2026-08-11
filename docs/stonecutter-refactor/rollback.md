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
