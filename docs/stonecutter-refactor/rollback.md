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
- `051aaaca42bdc980a3260d1a6c6fcd4404f228b7`: complete all remaining Java 21 range candidates, capability floors, and exact runtime gates. Revert before removing their source-family definitions.
- `a3402866b217ac159d6a3cec70d3585028f79732`: latest fully verified checkpoint. It promotes all exact rows, adds the resumable 62-cell controller and per-version Fabric natives, consolidates canonical shared sources, and adds the source-metrics gate. Revert this first to restore per-adapter source ownership and the prior publication ledger.
- `71f29b116f40e967996fa5e75e45aad89a7b0bb6`: latest continuation baseline. It adds the honest phase audit and portable deterministic source/ownership/branch metrics with exact-duplicate enforcement. Revert this checkpoint to return to the earlier limited metrics task; it does not alter PackForge runtime behavior.
- `0c2fb7bcff5dd634254f3ad25b205b896acfe34e`: operation-level Quick Pack ownership and phase-safe loader metadata. Revert this checkpoint to restore the prior early whole-mixin suppression; do not treat its build/package proof as real Quick Pack runtime evidence.

The unrelated `.github/ISSUE_TEMPLATE/bug-report.yml` edit remains unstaged and is not part of any rollback.
