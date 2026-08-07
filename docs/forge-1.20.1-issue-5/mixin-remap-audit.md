# Forge 1.20.1 mixin and refmap audit

## Broken artifact

The final `1.3.3-beta.1` Forge JAR declares `packforge.refmap.json` in both required mixin configs.

### `SimpleReloadInstanceMixin`

- Source method selector: `method_18368`
- Invocation target: `PreparableReloadListener#reload(...)`
- Final refmap: remaps the invocation target to `m_5540_`; no mapping for `method_18368`
- Final class constant: raw `method_18368` remains
- Runtime result: first fatal `InvalidInjectionException`

Mapped 1.20.1 source provides a stable alternative: the protected `SimpleReloadInstance` constructor invokes `SimpleReloadInstance.StateFactory#create(...)` once per listener. The repair wraps that invocation from method `<init>`.

### `SpriteLoaderMixin`

- Source method selector: `method_47660`
- Final refmap: mappings exist for `loadAndStitch`, `runSpriteSuppliers`, and `stitch`; no mapping for `method_47660`
- Final class constant: raw `method_47660` remains
- Runtime result: not reached before the first fatal mixin

Mapped legacy sources show one `CompletableFuture.supplyAsync(Supplier, Executor)` invocation in the source-list stage of `loadAndStitch`. The repair wraps that stable JDK invocation, times the supplier inside the original executor, and associates the returned source list with the atlas before `runSpriteSuppliers` consumes it.

The same raw selector existed in shared 1.21.1/1.21.4/1.21.8 and 1.21.11 adapters, so those adapters receive the same structural correction.

## Other 1.20.1 mixins

Artifact verification now checks every configured mixin entry has a packaged class. Forge/NeoForge configured mixin class constants are scanned for raw Fabric intermediary selectors, and the exact 17-artifact bundle is checksum-verified before runtime jobs. Fabric/NeoForge smoke consumes the verified `build/libs` bytes through `PACKFORGE_ARTIFACT_INPUT_DIR`. This remains structural evidence, not final-JAR runtime acceptance for Forge: Forge CI is source-mode, while production final-JAR validation uses `scripts/Smoke-Forge-Production.ps1`. Runtime status for the 18-cell matrix is recorded in `release-validation.md`.

## Fallback rule

If any structural hook does not have unit cardinality or fails a final-JAR smoke, exclude only that exact feature from the affected loader artifact and remove its capability declaration. Do not add `require = 0`, guess SRG names, or disable ZIP indexing.
