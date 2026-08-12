# Current source metrics

Generated deterministically by `./gradlew.bat reportSourceMetrics`. The checked-in copy is refreshed only with `updateSourceMetricsCheckpoint` at a verified stable checkpoint. Tests, generated sources, build output, documentation, and vendored third-party code are excluded.

## Summary

| Metric | Value |
|---|---:|
| Production Java files | 207 |
| Production nonblank LOC | 16129 |
| Bridge files | 103 |
| Bridge LOC | 5303 (32.88%) |
| Exact duplicate groups | 0 |
| Normalized version duplicate groups | 0 |
| Platform `target.key` references | 80 |
| Target-key/version conditional lines | 84 |
| Version-specific configuration renderers | 4 |
| Exact release cells | 22 |
| Exact loader cells | 62 |
| Registered build targets | 19 |
| Publication artifacts | 20 |
| Quick Pack implementation references | 0 |

## Source layers

| Layer | Files | Nonblank LOC |
|---|---:|---:|
| `neutralCommon` | 61 | 5972 |
| `versionCommon` | 46 | 4959 |
| `loaderCommon` | 15 | 480 |
| `exactVersionBridge` | 85 | 4718 |

Classification is exclusive and precedence-ordered: root `common` is neutral common; platform and version-loader directories are loader common; remaining version-local mixin/bridge/compat paths are exact-version bridges; all other Minecraft-facing version source is version common. Aggregate bridge files/LOC include loader-owned bridge/compat files as well as the non-loader `exactVersionBridge` layer.

## Duplicate groups

### Exact

None.

### Comment/whitespace-normalized version files

None.

## Version-specific configuration renderers

- `versions/mc1_20_1/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java`
- `versions/mc1_21_11/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java`
- `versions/mc26/common/src/client/java/com/teenkung/packforge/client/config/PackForgeConfigScreen.java`
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
