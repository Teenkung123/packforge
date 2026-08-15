# Current source metrics

Generated deterministically by `./gradlew.bat reportSourceMetrics`. The checked-in copy is refreshed only with `updateSourceMetricsCheckpoint` at a verified stable checkpoint. Tests, generated sources, build output, documentation, and vendored third-party code are excluded.

## Summary

| Metric | Value |
|---|---:|
| Production Java files | 217 |
| Production nonblank LOC | 16577 |
| Bridge files | 110 |
| Bridge LOC | 5553 (33.50%) |
| Exact duplicate groups | 0 |
| Normalized version duplicate groups | 0 |
| Platform `target.key` references | 18 |
| Target-key/version conditional lines | 15 |
| Platform target conditional lines | 0 |
| Phase F raw reduction | 262 LOC (1.56%) |
| Phase F mandatory added LOC ledger | 2,347 |
| Phase F ledger-adjusted reduction | 2,607 LOC (15.47%) |
| Phase F required reduction | 3,368 LOC (20%) |
| Phase F remaining shortfall | 761 LOC |
| Phase F metric gate | FAIL |
| Phase F status | IMPLEMENTED_UNVERIFIED |
| Configuration renderer bodies | 3 |
| Configuration renderer adapters | 2 |
| Exact release cells | 22 |
| Exact loader cells | 62 |
| Registered build targets | 19 |
| Publication artifacts | 20 |
| Quick Pack implementation references | 0 |

## Phase F source-reduction ledger

| Ledger field | Value |
|---|---:|
| Baseline commit | 609270f533666e7636d11f7d16590be925ec836f |
| Baseline production files | 212 |
| Baseline production nonblank LOC | 16,837 |
| Baseline normalized duplicate groups | 20 |
| Baseline normalized surplus LOC | 2,375 |
| Mandatory added LOC | 2,347 |
| Required reduction | 3,368 LOC (20%) |
| Ledger-adjusted reduction | 2,607 LOC (15.47%) |
| Remaining shortfall | 761 LOC |
| Platform branch baseline | 4 conditional lines |
| Platform branch reduction | 0 remaining (100.00%) |
| Gate result | FAIL |
| Phase F status | IMPLEMENTED_UNVERIFIED |

The added-LOC ledger counts nonblank production-Java additions from the committed baseline tree, excluding exact-content renames/copies, tests, generated sources, build output, documentation, and resources. Gross historical insertions are not used: the current-tree ledger is 2,347 LOC.

| Production root | Mandatory added LOC |
|---|---:|
| common | 1,051 |
| fabric | 146 |
| platform | 256 |
| versions | 894 |
| Total | 2,347 |

Gate detail: normalized duplicate reduction PASS (0 of 20 groups remain), no byte-identical production group PASS, version-common LOC remains below neutral-common LOC PASS, target-key platform-conditional reduction PASS (0 remaining), and the ledger-adjusted 20% shrink gate FAIL (761 LOC short). The report task records these results; validatePhaseFSourceReduction validates the ledger/status and reports the expected cleanup-gate failure without mislabeling this implementation as complete.

## Source layers

| Layer | Files | Nonblank LOC |
|---|---:|---:|
| `neutralCommon` | 63 | 6293 |
| `versionCommon` | 47 | 4832 |
| `loaderCommon` | 22 | 732 |
| `exactVersionBridge` | 85 | 4718 |

Classification is exclusive and precedence-ordered: root `common` is neutral common; canonical loader roots (`fabric`, `forge`, and `neoforge`), platform directories, and version-loader directories are loader common; remaining version-local mixin/bridge/compat paths are exact-version bridges; all other Minecraft-facing version source is version common. Aggregate bridge files/LOC include canonical and platform loader-owned bridge/compat files as well as the non-loader `exactVersionBridge` layer.

## Duplicate groups

### Exact

None.

### Comment/whitespace-normalized version files

None.

## Configuration renderer bodies

- `versions/mc1_20_1/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java`
- `versions/mc26/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java`
- `versions/shared/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreenBase.java`

## Configuration renderer adapters

- `versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java`
- `versions/shared/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java`

## Capability ownership

| Capability | Policy owner | Implementation proof classes | Mixin hooks |
|---|---|---|---|
| `ATLAS_CAP` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/CappedSpriteResourceLoader.class`<br>`com/teenkung/packforge/client/atlas/SpriteCap.class` | `client:atlas.SpriteLoaderMixin`<br>`client:atlas.TextureAtlasMixin` |
| `ATLAS_DECODE_BATCHING` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/AtlasTimings.class` | `client:atlas.SpriteLoaderMixin` |
| `ATLAS_MIP_PARALLEL` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/AtlasTimings.class` | `client:atlas.SpriteLoaderMixin` |
| `ATLAS_PHASE_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/AtlasTimings.class` | `client:atlas.SpriteLoaderMixin`<br>`client:atlas.TextureAtlasMixin` |
| `ATLAS_RETRY` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/AtlasRetry.class` | `client:atlas.SpriteLoaderMixin` |
| `FONT_BITMAP_CACHE` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/font/FontBitmapProviderCache.class` | `client:font.BitmapProviderDefinitionMixin` |
| `FONT_PROVIDER_PRESELECTION` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/font/FontOptimizationState.class`<br>`com/teenkung/packforge/client/font/FontSelectionRegistry.class` | `client:font.FontProviderSelectionMixin`<br>`client:font.FontSetMixin` |
| `FONT_RELOAD_DIAGNOSTICS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/font/FontOptimizationState.class`<br>`com/teenkung/packforge/client/font/FontReloadDiagnostics.class` | `client:font.FontManagerMixin` |
| `IMMEDIATELY_FAST_FONT_ATLAS_COMPAT` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/compat/ImmediatelyFastFontAtlasCompat.class` | `client-any:compat.ShaderManagerCompatMixin`<br>`client-any:options.OptionsMixin` |
| `LOADER_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/LoaderTimings.class` | `main:observe.ReloadableResourceManagerMixin` |
| `LOADING_FADE_CONTROL` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadStatus.class` | `client:ui.LoadingOverlayMixin` |
| `LOADING_STATUS_OVERLAY` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadStatus.class` | `client:ui.LoadingOverlayMixin` |
| `MODEL_ADAPTIVE_BATCHING` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/model/ModelBatchPlan.class`<br>`com/teenkung/packforge/client/model/ModelParseOptimizer.class` | `client:model.ModelManagerMixin` |
| `MODEL_DUPLICATE_CACHE` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/model/ModelBatchPlan.class`<br>`com/teenkung/packforge/client/model/ModelParseOptimizer.class` | `client:model.ModelManagerMixin` |
| `MODEL_PARSE_BATCHING` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/model/ModelBatchPlan.class`<br>`com/teenkung/packforge/client/model/ModelParseOptimizer.class` | `client:model.ModelManagerMixin` |
| `MODEL_PARSE_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/model/ModelParseOptimizer.class`<br>`com/teenkung/packforge/client/model/ModelParseTimings.class` | `client:model.ModelManagerMixin` |
| `MODEL_UV_TRANSPARENCY_CLAMP` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/SpriteCap.class` | `client:model.FaceBakeryMixin` |
| `RELOAD_LISTENER_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadListenerTelemetry.class` | `main:observe.SimpleReloadInstanceMixin` |
| `RELOAD_SUMMARY_TOAST` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | None | `client:ui.LoadingOverlayToastMixin` |
| `RESOURCE_PACK_INDEX` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/PackIndex.class`<br>`com/teenkung/packforge/loader/PackIndexCache.class` | `main:loader.FilePackResourcesMixin` |
| `SHADER_STALL_DIAGNOSTICS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadListenerTelemetry.class` | `main:observe.SimpleReloadInstanceMixin` |
| `STARTUP_ASYNC_CLASS_SCAN` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupAsyncFeatures.class` | `client:startup.MainStartupMixin` |
| `STARTUP_ASYNC_DATA` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupAsyncFeatures.class` | `client:startup.MinecraftStartupMixin` |
| `STARTUP_ASYNC_FONT_ATLAS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupAsyncFeatures.class` | `client:startup.MinecraftStartupMixin` |
| `STARTUP_EXECUTOR_TUNING` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupExecutorTuner.class` | `main:startup.UtilExecutorMixin` |
| `STARTUP_OPTIMIZER` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupEarlyConfig.class` | `client:startup.MainStartupMixin`<br>`client:startup.MinecraftStartupMixin`<br>`main:startup.UtilExecutorMixin` |
| `STARTUP_STATUS_OVERLAY` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupStatus.class` | `client:startup.GuiStartupMixin` |
| `STARTUP_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupTimings.class` | `client:startup.MainStartupMixin`<br>`client:startup.MinecraftStartupMixin` |
| `ZIP_READ_POOL` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ZipFilePools.class`<br>`com/teenkung/packforge/loader/ZipReadPool.class` | `main:loader.FilePackResourcesMixin` |
