# Performance rework work log

## Phase A - baseline and inventory

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

## Phase C - immutable reload context and low-overhead status

- Captured all reload-hot capability/config decisions in one immutable snapshot owned by an identity-bearing reload context.
- Normal status mode observes one listener future while preserving the original executor and future identities; per-task wrappers are reserved for detailed timings.
- Stale completion cannot clear a newer reload context. Failure and cancellation paths finish exact-context state.
- Added focused snapshot, stale-completion, failure cleanup, executor identity, listener boundary, readiness, and counter tests.
- Forge 1.20.1 and Fabric 26.1 focused builds/tests passed in the delegated slice; the main serialized clean builds below independently exercised the integrated hooks.
- Gate verdict: `PASS_WITH_NOTES`; runtime overlay behavior remains unexecuted.

## Phase D - operation-level FilePack compatibility

- Replaced broad cancellable resource/list methods with five narrow operations per adapter: one `ZipFile.getEntry`, two `ZipFile.entries`, and two `IoSupplier.create` seams.
- Every supplier hook calls the wrapped original exactly once before optional pooling.
- Removed all five modern `CompositePackResourcesMixin` implementations and registrations.
- Added source/artifact gates rejecting raw intermediary selectors, Composite registration/packaging, and broad FilePack return replacement.
- Delegated target builds passed for Group A: Fabric/Forge on 1.20.1, 1.21.1, 1.21.4, and 1.21.8; Group B: Fabric/Forge/NeoForge on 1.21.11 and 26.1.
- Gate verdict: `PASS_WITH_NOTES`; build/package proof is not runtime compatibility proof.

## Phase E - bounded ordered scheduling and sprite decode

- Added an ordered asynchronous mapper with `O(worker count)` worker futures, supplied-executor ownership, first-failure propagation, cancellation, and exact partial-result disposal.
- Reworked sprite decode for 1.20.1, shared 1.21, 1.21.11, and 26.x to use bounded workers while preserving original resource loaders and null-filtered order.
- Removed the 26.x broad PackForge `loadAndStitch`/`stitch` replacement; vanilla stitch control flow is authoritative.
- Removed unbounded telemetry owner maps; unknown atlas labels are preferred to retained list identity.
- Main serialized clean builds/tests passed for Fabric 26.1 and Forge 1.20.1 after reload/FilePack/sprite integration.
- Gate verdict: `PASS_WITH_NOTES`; scheduling allocation and live output parity measurements remain pending.

## Phase F - optimization safety gate

- `validateTargetRegistry` now scans all mixin sources for raw intermediary selectors and validates one narrow FilePack adapter per target.
- Artifact verification requires exact operation-level ZIP constants and rejects Composite or broad return-cancelling FilePack bytecode.
- Source gate passed. Artifact gate will be exercised by the final 17-artifact build.
- Gate verdict: `PASS_WITH_NOTES`.

## Phase G - hook-preserving model scheduling

- All six model adapters call the original vanilla `loadBlockModels` implementation; the prior direct parser is unreachable.
- With no model feature request the exact original executor is preserved. Requested model features use a supplied-executor bounded FIFO drainer with no owned pool.
- The compatibility guard remains an unconditional `ORIGINAL` path for platform model-loading extensions.
- Neutral tests cover bounded delegate submissions, FIFO/direct/reentrant/one-thread behavior, rejection without running the rejected command, separate state, and fail-closed strategy selection.
- Focused neutral tests plus Fabric/Forge 1.20.1 test compilation passed in the delegated slice.
- Vanilla per-model futures are still allocated by the original implementation; only delegate scheduling pressure is coalesced.
- Gate verdict: `PASS_WITH_NOTES`; model wall/allocation profiling and live extension-mod parity remain pending.

## Phase H - invocation-scoped atlas retry ownership

- Commit: `dde13ee`.
- Removed global sprite-ID ownership and duplicate native-image retention from the mc26 retry path.
- Atlas state is bound to one reload/atlas invocation and associated by `SpriteContents` identity. Overlapping reloads and identical atlas IDs cannot release each other's state.
- Every attempt invokes original vanilla stitch. Only exact stitch failures are eligible; unrelated runtime failures propagate unchanged.
- Failed/replaced contents are closed once. Successful output ownership remains with vanilla. Retry is default-off, atlas cap stays on the original path, and ResourcePack Unbounded remains authoritative.
- Focused Fabric tests plus Fabric/Forge/NeoForge mc26 compilation passed. No live retry-failure profile was executed.
- Gate verdict: `PASS_WITH_NOTES`; ten-reload native/RSS slope remains unmeasured.

## Phase I - unique-stack font preparation and epoch-safe bitmap cache

- Commit: `484f077`.
- Shared 1.21, 1.21.11, and mc26 adapters group ordered provider stacks by provider/filter identity and compute one selection per unique stack through `OrderedAsync`.
- Normal apply resolves by captured font ID in O(1); provider-list lookup remains compatibility fallback. The shared 1.21 `apply` method has exactly one `Map.forEach` anchor in mapped 1.21.1, 1.21.4, and 1.21.8 bytecode.
- 1.20.1 intentionally keeps vanilla provider selection because the target does not advertise `FONT_PROVIDER_PRESELECTION`; bitmap caching and diagnostics remain supported.
- Bitmap cache lookup/load/publish captures one reload epoch. Reset, lookup, and publication are serialized; stale loads return directly to their old owner and never enter a newer generation. Keys include resource-manager identity and providers close through idempotent refcounts.
- Focused 1.21.1 tests passed (103 tests, including unique-stack and stale-epoch cases). 1.20.1 Fabric/Forge, 1.21.11 Fabric/Forge, and mc26 Fabric/Forge/NeoForge builds/tests passed.
- Gate verdict: `PASS_WITH_NOTES`; live font parity and ImmediatelyFast profiles remain unexecuted.

## Phase J - independent-review async lifecycle repair

- Commit: `4b92976`.
- Independent review found that overlapping reload work could observe a newer global snapshot and that completed `OrderedAsync` futures retained disposed result slots.
- Six `createReload` adapters now bind the exact invocation context around original creation. Listener executor tasks carry that context across async boundaries and nested bindings restore the prior scope.
- Ordered mapping clears owned result slots and detaches mapping state on success, failure, rejection, and cancellation while late produced values still dispose exactly once.
- Twenty-one focused Fabric tests passed; Forge 1.20.1 `testClasses`, raw-selector scan, and `git diff --check` passed.
- Gate verdict: `PASS_WITH_NOTES`; two final Luna re-review attempts were later timeboxed and stopped without a verdict, so they are not counted as review passes.

## Phase K - final artifact integration

- `validateTargetRegistry` passed after all implementation slices.
- A pre-final root build exposed a namespace-blind verifier check: remapped Fabric artifacts correctly contain intermediary `class_7367` for `IoSupplier`, while the gate required the named owner. Child compilation/tests passed before this verification-only failure.
- Commit `111f2a9` made the operation audit namespace-aware while retaining exact anchor cardinality. Commit `a72f628` scoped client-only font tests to Fabric so NeoForge's server/common test classpath is not required to expose client classes.
- The first uninterrupted clean matrix passed before runtime smoke. After the runtime repair in Phase L, `gradlew.bat clean buildAllSupported verifyAllArtifacts --no-daemon` passed again in 3m21s with 32/32 tasks executed and exactly 17 artifacts.
- `verifyExistingArtifacts` passed independently. Final artifacts contain no raw intermediary source selectors.
- Gate verdict: `PASS_WITH_NOTES`; clean build/test/package proof is complete, live runtime coverage remains bounded below.

## Phase L - production Forge runtime repair

- Production Forge 47.4.22 exact-JAR smoke exposed a MixinExtras runtime rule not covered by compilation: `@Local` sugar preceded the `Operation` argument in FilePack `@WrapOperation` handlers. All six adapters now keep sugar trailing, and registry validation rejects the invalid ordering.
- The next run exposed that Forge 1.20.1 production SRG places `ZipFile.getEntry` and the first `IoSupplier.create` in private `getResource(String)`, not public `getResource(PackType, ResourceLocation)`. The 1.20.1 hooks now target the private owner, and registry validation requires that selector twice for every adapter.
- Repair commit: `bef22bc`.
- Rebuilt artifact SHA-256: `d8fe15c791282cf1acd893003246662a4daa97d1ba72e39510360b940d29adbd`.
- Exact final-JAR startup passed on Forge 47.4.22 and the minimum supported Forge 47.0.0. Both used `ReloadCount=0` and controlled termination after readiness; no clean-exit or F3+T claim is made.
- Two bounded final Luna re-review attempts remained running beyond their timeboxes and were stopped without a verdict. The earlier independent review and its repaired findings remain recorded; no second-review pass is claimed.
- Gate verdict: `PASS_WITH_NOTES`; the reported startup crash and the optimization-introduced Mixin failures are absent in the executed endpoint profiles.
