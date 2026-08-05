# Mixin anchor inventory

This inventory records committed hotfix-base hooks. Planned anchors are not marked verified until the corresponding target compiles and the packaged mixin/refmap checks pass.

## Reload lifecycle

| Adapter | `SimpleReloadInstance` structural hook | `ReloadableResourceManager` lifecycle hook |
|---|---|---|
| `mc1_20_1` | `<init>` invokes `StateFactory#create(PreparationBarrier, ResourceManager, PreparableReloadListener, Executor, Executor)` once | `createReload` HEAD/RETURN |
| `mc1_21_1` | same constructor/StateFactory shape as 1.20.1 | `createReload` HEAD/RETURN |
| `mc1_21_4` | same constructor/StateFactory shape as 1.20.1 | `createReload` HEAD/RETURN |
| `mc1_21_8` | `prepareTasks` invokes `StateFactory#create(PreparationBarrier, ResourceManager, PreparableReloadListener, Executor, Executor)` | `createReload` HEAD/RETURN |
| `mc1_21_11` | `prepareTasks` invokes `StateFactory#create(SharedState, PreparationBarrier, PreparableReloadListener, Executor, Executor)` | `createReload` HEAD/RETURN |
| `mc26_1_to_26_2` | same SharedState structural shape as 1.21.11 | `createReload` HEAD/RETURN plus startup timing/status |

The Forge 1.20.1 packaged refmap maps the constructor/StateFactory target and contains no raw intermediary selector. Performance work must not reintroduce `method_<number>` or a synthetic helper selector.

## File-pack/archive ownership

| Adapter group | Archive state owner | Current public replacement | Planned narrow operation seams |
|---|---|---|---|
| `mc1_20_1` | `FilePackResources` | cancellable `getResource`, `getNamespaces`, `listResources` | private lookup `ZipFile.getEntry(String)`; namespace/list `ZipFile.entries()`; optional `IoSupplier.create(ZipFile, ZipEntry)` |
| `mc1_21_1`, `mc1_21_4`, `mc1_21_8` | `SharedZipFileAccess` | same three cancellable public methods | same ZIP operation seams; retain SharedZip lifecycle bridge |
| `mc1_21_11` | `SharedZipFileAccess` | same three methods using `Identifier` API | same ZIP operation seams |
| `mc26_1_to_26_2` | `SharedZipFileAccess` via accessor bridge | same three methods | same ZIP operation seams and accessor ownership |

The operation hooks must invoke the original operation when the index is disabled, absent, failed, closed, or bound to a different `ZipFile`. Minecraft remains responsible for prefix construction, validation/warnings, directory filtering, output callbacks, null behavior, and close behavior.

## Composite packs

`CompositePackResourcesMixin` exists for every modern adapter (`mc1_21_1` and later) and broadly cancels `getResource`, `listResources`, and `getNamespaces`. It does not exist on `mc1_20_1`. The provisional decision is removal after indexed FilePack hooks land; a broad replacement may survive only if a controlled overlay benchmark shows at least 3% end-to-end value with exact winner/order/identity parity.

## Sprite/atlas

- Legacy adapters use a structural `CompletableFuture.supplyAsync(Supplier, Executor)` source hook inside `loadAndStitch`, then replace `runSpriteSuppliers` only when decode batching is enabled.
- `mc26` currently replaces `loadAndStitch` and `stitch`; this is not an acceptable final narrow-hook architecture.
- Any retry-capable target must invoke original stitch on every attempt and use atlas/object-scoped ownership rather than sprite ID alone.

## Model/font

- Current model batching can bypass loader extension hooks; future strategy must be explicit `ORIGINAL`, `COALESCED_ORIGINAL`, or proven-safe `DIRECT_BATCHED` per loader/target.
- Current font selection memoization is not single-flight and apply scans provider lists. The target is one computation per unique structural stack and O(1) font-ID apply while calling original font construction.

## Verification requirements

For each adapter, compile-time Mixin processing, configured-class packaging, operation-target cardinality, effective mixin JSON, non-Fabric raw-selector scanning, and Forge 1.20.1 refmap checks must pass. Runtime evidence remains separate from build/refmap evidence.

