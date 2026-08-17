# ZIP read pool default-promotion evidence

Checked: 2026-08-17

This checkpoint promotes only `loaderZipPoolEnabled`. It does not promote the font bitmap cache, sprite decode batching, atlas retry, startup optimizer, or any unavailable option.

## Decision boundary

The project owner accepted the existing third-party compatibility results and explicitly waived another broad compatibility sweep solely for this default change. The promotion remains fail-closed:

- the ZIP pool is PackForge-owned;
- it is effective only while the PackForge resource index is effective;
- Quick Pack ownership disables the PackForge index, so the dependent ZIP pool becomes inactive without being falsely attributed to Quick Pack;
- disabling the reload optimizer or resource index also disables the pool;
- an existing explicit `loaderZipPoolEnabled=false` remains false;
- configurations missing the field receive the promoted default.

## Existing runtime signal

The focused Fabric/Sodium final-JAR comparison recorded:

| mode | median reload |
|---|---:|
| baseline | 632 ms |
| ZIP pool only | 590 ms |

The isolated ZIP-pool run preserved the same semantic resource hash, completed ten reloads, and exited cleanly. This is a 6.6% lower median for that workload. The prior three-feature combined regression is not used as evidence for enabling the other two options.

Source: `docs/stonecutter-refactor/evidence/default-candidates-2026-08-16.md`.

## Lifecycle and configuration evidence

Focused source tests cover:

- bounded concurrent auxiliary handles;
- closing all handles and releasing the Windows file lock;
- permanent vanilla fallback after close;
- one-time failure reporting and fallback;
- index/pool invalidation and idempotent archive closure;
- default-on behavior only when both index and pool capabilities are effective;
- automatic suppression when Quick Pack owns resource indexing;
- preservation of an explicit stored `false`;
- adoption of the new default when the field is absent.

## Gate disposition

- `semanticParity`: PASS — isolated final-JAR run retained the baseline resource hash.
- `stability`: PASS — isolated final-JAR run completed ten reloads and exited cleanly.
- `lifecycle`: PASS — bounded handle and archive-state close/invalidation tests cover the pool lifecycle.
- `performance`: PASS — isolated run improved median reload time from 632 ms to 590 ms.
- `compatibility`: PASS — broad retesting was explicitly waived; dependency gating prevents concurrent PackForge index/pool operation in Quick Pack mode.
- `configuration`: PASS — source/default contract and explicit-false preservation tests cover migration behavior.

The pull-request CI result remains the implementation acceptance boundary for compilation and focused tests.
