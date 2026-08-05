# PackForge crash hotfix and performance rework report

## Scope and revisions

- Audited repository revision: `086f3f8686109e81263a155bbc3fd3caa43af611`.
- Crash-only rollback base: `4fafbef4b95c2d39bc67131256d34f170195fbc6`.
- Performance branch: `codex/forge-1.20.1-performance`.
- Implementation revision: `bef22bc` (final evidence/docs commit follows).
- No release was published by this work.

Optimization checkpoints after the rollback base:

| Commit | Phase |
|---|---|
| `19041c7` | compact one-pass archive index |
| `48a96f1` | bounded ordered mapper |
| `e2aedd2` | immutable reload snapshot and telemetry trim |
| `8eaf1ab` | operation-level FilePack hooks |
| `ad30efb` | bounded ordered sprite decode |
| `fb271b8` | unsafe-mixin source/artifact gate |
| `640e3dd` | hook-preserving model scheduling |
| `fafe190` | fail-closed shared-ZIP bridge |
| `dde13ee` | invocation-scoped atlas retry ownership |
| `4b92976` | exact async reload context and retention repair |
| `484f077` | unique-stack font preparation and epoch-safe bitmap cache |
| `111f2a9` | namespace-aware remapped operation-anchor verifier |
| `a72f628` | client-only font test scoping |
| `bef22bc` | production MixinExtras signature and Forge 1.20.1 selector repair |

## Forge 1.20.1 incident and replacement artifact

Issue #5 is `CONFIRMED_FROM_ISSUE_LOG`. The published `1.3.3-beta.1` Forge artifact retained raw Fabric/Yarn enclosing selectors `method_18368` and `method_47660`. Required mixin application failed first in `SimpleReloadInstanceMixin`; the generated refmap mapped the invocation but not the enclosing synthetic selector.

The hotfix replaces both selectors with structural hooks:

- `SimpleReloadInstance.StateFactory#create` inside the stable reload constructor/preparation path;
- `CompletableFuture.supplyAsync(Supplier, Executor)` inside stable atlas loading.

The replacement Forge artifact is `packforge-forge-1.3.3-beta.2-mc1.20.1.jar`. The crash-only rollback artifact SHA-256 was `0505099080d0e64b5fa468f2fc0447987eac3951e8fae6d957218d852ae1e85e`. The final integrated artifact SHA-256 is `d8fe15c791282cf1acd893003246662a4daa97d1ba72e39510360b940d29adbd`. That exact final artifact reached readiness without a fatal mixin signature on Forge `47.0.0` and `47.4.22`; the earlier crash-only artifact also reached atlas creation on `47.4.20`. These runs do not prove F3+T completion or clean UI exit.

Issue attachment URLs, retrieval hashes, causal logs, broken/fixed refmap details, and the beta.2 artifact audit are in `docs/forge-1.20.1-issue-5/`. Runtime-smoke workflows now define the 17 supported target/platform cells plus the extra reporter Forge row. Fabric/NeoForge use checksum-verified packaged artifacts; Forge CI is source-mode, with final SRG-JAR acceptance handled by `scripts/Smoke-Forge-Production.ps1`.

## Architecture before and after

Before:

```text
FilePack public-method cancellation -> duplicated validation/control flow
unbounded per-item futures -> transient task pressure
direct model parser -> loader/model hooks bypassed
font apply provider-list scan -> repeated stack computation
global atlas sprite-ID cache + clones -> collision/leak risk
global latest reload snapshot -> overlap can read newer settings
```

After:

```text
vanilla FilePack flow
  -> five narrow ZIP operations
  -> one compact archive index with bounded caches

supplied executor
  -> O(worker-count) ordered mapper
  -> sprite/font bounded work, stable output order, exact cleanup

vanilla model loader
  -> exact original executor when inactive/guarded
  -> bounded coalescing executor only when requested

reload invocation identity
  -> immutable feature snapshot
  -> executor-bound task scope
  -> listener/model/font/atlas work reads exact context

atlas invocation identity
  -> original stitch on every attempt
  -> identity-owned replacements
  -> exact success/failure cleanup
```

## Implementation by area

### ZIP index and FilePack

`PackIndex` performs one central-directory enumeration into `IndexedEntry[]` plus primitive sorted ordinals. Exact lookup is populated during enumeration; only duplicate-path canonicalization calls `ZipFile.getEntry`. Prefix and namespace caches are bounded and cleared with archive invalidation.

Every adapter uses one `ZipFile.getEntry`, two `ZipFile.entries`, and two `IoSupplier.create` operation hooks. Original operations remain authoritative when indexing/pooling is disabled, unavailable, stale, failed, or bound to a different archive. All broad modern `CompositePackResourcesMixin` implementations were removed.

Production Forge testing found two additional runtime-only constraints. MixinExtras sugar parameters must trail the `Operation` parameter, and Forge 1.20.1's production SRG bytecode owns the direct lookup/supplier operations in private `getResource(String)`. Commit `bef22bc` corrects every adapter's parameter order, corrects the 1.20.1 owner selector, and adds source gates for both invariants.

### Reload and scheduling

Reload flags are captured once in `ReloadFeatureSnapshot`. Each reload has an identity-bearing context; all six `createReload` adapters bind it around original creation, and supplied listener executors carry it into queued work. Normal status observes listener futures without detailed timing counters; detailed task timing stays opt-in.

`OrderedAsync` uses at most `min(chunkCount, max(1, workerBudget * 2))` worker tasks, preserves order/nulls, propagates the first deterministic failure, disposes partial/late outputs once, and detaches internal state after completion/cancellation.

### Models and sprites

All six model adapters invoke vanilla `loadBlockModels`. Inactive and compatibility-guarded paths retain the exact original executor. Requested optimization uses a bounded FIFO coalescing executor; the direct parser path is unreachable.

Sprite decode uses bounded ordered work on every adapter while preserving the original platform resource loader. mc26 no longer replaces complete `loadAndStitch` control flow. ResourcePack Unbounded ownership remains a hard bypass.

### Fonts

1.21+ font preparation groups ordered provider stacks by provider/filter identity and computes one selection per unique stack. Normal apply resolves by font ID in O(1); provider-list lookup is fallback only. The three shared 1.21 mapped clients each contain exactly one `Map.forEach` in `FontManager.apply`.

Bitmap cache publication captures one reload epoch before lookup/load. Reset, lookup, and publication are serialized; stale loads remain with the old owner and cannot enter a newer generation. Keys include resource-manager identity and wrappers use idempotent refcounted close. Minecraft 1.20.1 intentionally retains vanilla provider selection because that target does not advertise preselection.

### Atlas/native ownership

mc26 retry state belongs to one reload/atlas invocation and records `SpriteContents` by identity. Every retry calls original stitch. Only exact stitch failures are eligible; unrelated exceptions are unchanged. Replacements transfer ownership without retained clones, and failure/replacement cleanup closes each content once. Retry remains default-off. Atlas cap stays on the original path because a hook-preserving implementation was not proven.

## Deterministic benchmark and semantic evidence

Fixture:

- 20,022 entries; 5,057,823 bytes;
- SHA-256 `722e45e3cfedf4b5d9cf0761bb94e42ac7d860c70dc612d350485fd13da8aba3`;
- semantic hash `c5d57e4819095cf193b43c68df49e94f904634acf22225541bfbbb6a13f1e547`.

Five-run medians:

| Metric | Hotfix baseline | Compact candidate | Delta |
|---|---:|---:|---:|
| Index construction | 31.251 ms | 16.725 ms | 46.5% faster |
| Reference scan query | 83.437 ms | 78.603 ms | contextual only |
| Indexed query | 1.876 ms | 0.283 ms | 84.9% lower than prior index query |

The semantic hash matched in all baseline/candidate runs. This is ZIP microbenchmark parity, not a complete live resource/model/font/atlas manifest.

## Task, allocation, and lifecycle evidence

- Ordered scheduling has O(worker-count) task/future state and tests rejection, one-thread execution, cancellation, late values, deterministic failure, cleanup failure, and post-completion retention.
- The font fixture with two identical stacks and one reversed stack produces two unique groups for three font IDs; live pack unique-stack counts were not captured.
- Archive invalidation, stale reload completion, bitmap epoch reset, provider idempotent close, and atlas stale-overlap identity have focused tests.
- No JFR allocation recording, class histogram, post-GC heap measurement, native/RSS slope, or ZIP-handle slope was captured. No allocation, heap, native-memory, or leak percentage is claimed.

## Verification commands and evidence

Passed during implementation:

- `gradlew.bat validateTargetRegistry --no-daemon`;
- focused PackIndex fixture/benchmark tests and five baseline/five candidate invocations;
- 21 focused reload-context/ordered-retention tests;
- 103 focused 1.21.1 font tests after the epoch repair;
- 1.20.1 Fabric/Forge builds/tests;
- 1.21.11 Fabric/Forge builds/tests;
- mc26 Fabric/Forge/NeoForge builds/tests;
- mapped-bytecode audit: one `FontManager.apply` `Map.forEach` on 1.21.1, 1.21.4, and 1.21.8;
- raw intermediary-selector and `git diff --check` scans.

Final integration evidence:

- `gradlew.bat clean buildAllSupported verifyAllArtifacts --no-daemon`: passed in 3m21s, 32/32 root tasks executed;
- exactly 17 artifacts produced; `verifyExistingArtifacts` passed independently;
- final Forge 1.20.1 SHA-256 `d8fe15c791282cf1acd893003246662a4daa97d1ba72e39510360b940d29adbd`;
- exact final-JAR controlled startup passed on Forge 47.0.0 and 47.4.22;
- final benchmark sample: 20,022 entries, index build 18.070 ms, reference median 77.633 ms, indexed median 0.269 ms, semantic hashes equal.

## Independent review and repairs

The super-advisor recommended safety-first paths: original vanilla control flow when evidence is incomplete, hook-preserving coalescing for models, original stitch per atlas attempt, and default-off/evidence-gated experimental features.

The first independent implementation review returned `NEEDS_FOLLOW_UP` for:

- old async work observing a newer global reload context;
- completed/cancelled ordered mappings retaining internal result slots;
- modern shared-ZIP bridge casts/registration drift;
- artifact-gate precision.

Repairs landed in `fafe190`, `4b92976`, `111f2a9`, and `bef22bc`. The final verifier is namespace-aware without accepting absent anchors, and the production-smoke failures added guards for MixinExtras parameter ordering and the direct-operation owner selector. Two bounded Luna re-review attempts exceeded their timeboxes and were stopped without returning a verdict; they are not counted as review passes. The earlier independent findings and their objective repair/build/runtime evidence remain recorded.

## Compatibility outcomes

`docs/performance-rework/compatibility-matrix.md` is authoritative. Build/package proof is not runtime proof. Exact third-party profiles (Sodium/Iris, ImmediatelyFast, Continuity, ETF/EMF, Vulkan, ResourcePack Unbounded, Embeddium/Oculus, and CIT/model extensions) remain `UNTESTED` unless a matching installed profile is executed.

## P2 decisions

- ZIP read pooling remains default-off and optional.
- Broad Composite replacement was removed because no controlled >=3% end-to-end evidence justified its compatibility risk.
- Direct model parsing is disabled; hook-preserving original loading is authoritative.
- mc26 atlas cap is not installed because a hook-preserving implementation was not proven.
- Atlas retry remains default-off and bounded.

## Rollback and limitations

The crash-only rollback point is `4fafbef`. Each optimization area is a later isolated commit. Runtime flags and fallback behavior are listed in `rollback-and-disable.md`; unknown/unavailable config values remain preserved and config version 12 is unchanged.

Open acceptance items are reported, not inferred:

- automated F3+T and clean-exit final-JAR acceptance;
- live vanilla runtime cells outside the recorded Forge startup runs;
- deterministic full resource/model/font/atlas manifest parity;
- ten-reload native/handle slope;
- warm/cold end-to-end timing and allocation/heap gates;
- exact third-party compatibility profiles.
