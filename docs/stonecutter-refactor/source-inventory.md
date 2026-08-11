# Source inventory

Baseline production inventory is recorded in `baseline.md`.

## Current architecture

- Root `common` contains loader-neutral algorithms, config, policy inputs, diagnostics, and tests.
- `versions/<adapter>` contains Minecraft-facing shared/client code, mixins, descriptors, access wideners, and loader-specific legacy shims.
- `platform/fabric`, `platform/forge`, and `platform/neoforge` are standalone Gradle builds assembled from common, selected adapter, and loader sources.
- `gradle/minecraft-targets.json` selects exactly one adapter per target.

## Core seams to preserve

- `PackIndex`, `PackArchiveState`, `ZipReadPool`, and `ZipFilePools` own ZIP indexing/lifecycle.
- `OrderedAsync`, `CoalescingExecutor`, and `ModelSchedulingPlan` own bounded ordered work.
- `PackForgeConfig`, `PackForgeConfigScreenModel`, `PackForgeConfigDraft`, `PackForgeCapabilityProfile`, `FeatureFlags`, and `ReloadFeatureSnapshot` own current config/capability decisions.
- `ReloadExecutionContext`, `ReloadLifecycle`, `ReloadHooks`, and `ReloadSessionTracker` own reload identity/lifecycle.
- Root artifact checks in `build.gradle` enforce narrow operation-level hooks, bridge packaging, refmaps, metadata, class floors, and capability declarations.

## Duplication evidence

Normalized exact-file comparison found 20 duplicate groups across version production sources. Highest-value migration seams are repeated `ForgeModListCompat`, reload manager mixins, config screens, `PackForgeClient`, `RuntimeResourceHash`, and shared ZIP access mixins. This inventory is evidence for later capability-by-capability deduplication; it is not permission to delete adapters before parity tests pass.

## Version-specific boundaries

Keep Minecraft class names/descriptors, mixin targets, native widget differences, access wideners, and loader remap/refmap behavior in thin bridges. Move only loader-neutral algorithms and policy into shared sources after a focused parity test.
