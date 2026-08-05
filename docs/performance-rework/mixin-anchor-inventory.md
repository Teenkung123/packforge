# Mixin anchor inventory

This inventory records the implemented structural hooks. Build and packaged-artifact verification are recorded separately from live runtime acceptance.

## Reload lifecycle

| Adapter | `SimpleReloadInstance` structural hook | `ReloadableResourceManager` lifecycle hook |
|---|---|---|
| `mc1_20_1` | `<init>` invokes `StateFactory#create(PreparationBarrier, ResourceManager, PreparableReloadListener, Executor, Executor)` once | `@WrapMethod createReload`; exact context bound around original invocation |
| `mc1_21_1` | same constructor/StateFactory shape as 1.20.1 | same exact-context `createReload` wrapper |
| `mc1_21_4` | same constructor/StateFactory shape as 1.20.1 | same exact-context `createReload` wrapper |
| `mc1_21_8` | `prepareTasks` invokes `StateFactory#create(PreparationBarrier, ResourceManager, PreparableReloadListener, Executor, Executor)` | same exact-context `createReload` wrapper |
| `mc1_21_11` | `prepareTasks` invokes `StateFactory#create(SharedState, PreparationBarrier, PreparableReloadListener, Executor, Executor)` | same exact-context `createReload` wrapper |
| `mc26_1_to_26_2` | same SharedState structural shape as 1.21.11 | same exact-context `createReload` wrapper plus startup timing/status |

The Forge 1.20.1 packaged refmap maps the constructor/StateFactory target and contains no raw intermediary selector. Performance work must not reintroduce `method_<number>` or a synthetic helper selector.

## File-pack/archive ownership

| Adapter group | Archive state owner | Public replacement | Implemented narrow operation seams |
|---|---|---|---|
| `mc1_20_1` | `FilePackResources` | none | private `getResource(String)` owns one `ZipFile.getEntry(String)` and one `IoSupplier.create`; namespace/list paths own two `ZipFile.entries()` and the second `IoSupplier.create` |
| `mc1_21_1`, `mc1_21_4`, `mc1_21_8` | `SharedZipFileAccess` | none | same five operation seams; SharedZip lifecycle bridge retained and fail-closed |
| `mc1_21_11` | `SharedZipFileAccess` | none | same five operation seams using the `Identifier` API |
| `mc26_1_to_26_2` | `SharedZipFileAccess` via accessor bridge | none | same five operation seams and accessor ownership |

The operation hooks must invoke the original operation when the index is disabled, absent, failed, closed, or bound to a different `ZipFile`. Every `Operation` argument precedes trailing MixinExtras `@Local` sugar, as required by the runtime injector. Minecraft remains responsible for prefix construction, validation/warnings, directory filtering, output callbacks, null behavior, and close behavior.

## Composite packs

All five modern `CompositePackResourcesMixin` implementations and registrations were removed. The broad replacement had no controlled evidence meeting the required 3% end-to-end gate; vanilla overlay validation, winner order, and identity remain authoritative.

## Sprite/atlas

- Legacy adapters use a structural `CompletableFuture.supplyAsync(Supplier, Executor)` source hook inside `loadAndStitch`, then wrap `runSpriteSuppliers` only when decode batching is enabled.
- `mc26` preserves vanilla `loadAndStitch`; bounded decode wraps `runSpriteSuppliers`, retry wraps original `stitch`, and constructor-scoped recording is active only for one retry-enabled atlas invocation.
- Retry calls original stitch on every attempt, keys ownership by reload/atlas/object identity, closes replacements once, and remains default-off. ResourcePack Unbounded bypasses PackForge atlas ownership.

## Model/font

- Model adapters call original vanilla `loadBlockModels`. No feature request uses the exact original executor; requested model features use a bounded coalescing executor, while compatibility guards fail closed to `ORIGINAL`. The direct parser is unreachable.
- Font preparation groups ordered provider stacks by structural identity, computes once per unique stack through bounded ordered work, and resolves normal apply by font ID in O(1). Provider-stack lookup is compatibility fallback only. Bitmap cache entries are reload-epoch and resource-manager scoped with refcounted close.

## Verification requirements

For each adapter, compile-time Mixin processing, configured-class packaging, operation-target cardinality, `getResource(String)` ownership, trailing-sugar ordering, effective mixin JSON, non-Fabric raw-selector scanning, and Forge 1.20.1 refmap checks must pass. Runtime evidence remains separate from build/refmap evidence.
