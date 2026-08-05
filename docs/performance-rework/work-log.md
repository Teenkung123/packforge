# Performance rework work log

## Phase A — baseline and inventory

- Starting revision: `4fafbef4b95c2d39bc67131256d34f170195fbc6`.
- Worktree: clean before baseline commands.
- Commands: `gradlew.bat --version`, `validateTargetRegistry`, clean primary `buildTarget`, and six PackIndex benchmark invocations (one initial plus five recorded samples).
- Build/tests: primary Fabric, Forge, and NeoForge builds/tests passed.
- Semantic result: deterministic baseline/indexed hash matched at `c5d57e...1e547` in every recorded sample.
- Benchmark: five-sample median index construction `31.251 ms`; indexed query median `1.876 ms`; baseline scan median `83.437 ms`.
- Resource evidence: deterministic fixture identity recorded; retained heap/JFR/native/handle evidence not yet captured.
- Compatibility impact: none; documentation/evidence only.
- Deviation: runtime timing profiles unexecuted because the GUI-control backend is unavailable.
- Gate verdict: `PASS_WITH_NOTES`.

## Phase B - compact one-pass archive index

- Replaced the multi-lookup build with one central-directory enumeration, compact `IndexedEntry[]` storage, and a stable primitive ordinal sort for lexical traversal.
- Exact lookup is populated during enumeration; only duplicate-path canonicalization falls back to `ZipFile.getEntry`.
- Added bounded prefix and namespace caches with archive invalidation/close handling.
- Added empty, single-entry, 20k-entry, duplicate, ordering, directory, Unicode, concurrency, cache-bound, and invalidation tests.
- Five-sample median construction: `16.725 ms`, down `46.5%` from the clean hotfix baseline.
- Five-sample median indexed query: `0.283 ms`; reference scan median: `78.603 ms`.
- Semantic hash remained `c5d57e...1e547` for all candidate runs.
- Retained heap remains unmeasured; no memory percentage is claimed.
- Gate verdict: `PASS_WITH_NOTES`.
