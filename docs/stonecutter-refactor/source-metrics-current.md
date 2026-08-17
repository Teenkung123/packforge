# Current source metrics

Generated deterministically by `./gradlew.bat reportSourceMetrics`. The checked-in copy is refreshed only with `updateSourceMetricsCheckpoint` at a verified stable checkpoint. Tests, generated sources, build output, documentation, and vendored third-party code are excluded.

## Summary

| Metric | Value |
|---|---:|
| Production Java files | 216 |
| Production nonblank LOC | 16731 |
| Bridge files | 113 |
| Bridge LOC | 5671 (33.90%) |
| Exact duplicate groups | 0 |
| Normalized version duplicate groups | 0 |
| Platform `target.key` references | 18 |
| Target-key/version conditional lines | 6 |
| Platform target conditional lines | 0 |
| Phase F raw reduction | 106 LOC (0.63%) |
| Phase F mandatory added LOC ledger | 2347 |
| Phase F ledger-adjusted reduction | 2453 LOC (14.57%) |
| Phase F required reduction | 3368 LOC (20%) |
| Phase F remaining shortfall | 915 LOC |
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
| Baseline production nonblank LOC | 16837 |
| Baseline normalized duplicate groups | 20 |
| Baseline normalized surplus LOC | 2375 |
| Mandatory added LOC | 2347 |
| Required reduction | 3368 LOC (20%) |
| Ledger-adjusted reduction | 2453 LOC (14.57%) |
| Remaining shortfall | 915 LOC |
| Platform branch baseline | 4 conditional lines |
| Platform branch reduction | 0 remaining (100.00%) |
| Gate result | FAIL |
| Phase F status | IMPLEMENTED_UNVERIFIED |

Added-LOC basis: Nonblank production-Java additions from the committed baseline tree, with exact-content renames/copies excluded; tests, generated sources, build output, documentation, and resources are excluded.

| Production root | Mandatory added LOC |
|---|---:|
| common | 1051 |
| fabric | 146 |
| platform | 256 |
| versions | 894 |

## Source layers

| Layer | Files | Nonblank LOC |
|---|---:|---:|
| `neutralCommon` | 66 | 6994 |
| `versionCommon` | 42 | 4191 |
| `loaderCommon` | 22 | 740 |
| `exactVersionBridge` | 86 | 4806 |

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
| `ATLAS_PHASE_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/AtlasTimings.class` | `client:atlas.SpriteLoaderMixin`<br>`client:atlas.TextureAtlasMixin` |
| `ATLAS_RETRY` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/AtlasRetry.class` | `client:atlas.SpriteLoaderMixin` |
| `FONT_BITMAP_CACHE` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/font/FontBitmapProviderCache.class` | `client:font.BitmapProviderDefinitionMixin` |
| `FONT_PROVIDER_PRESELECTION` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/font/FontOptimizationState.class`<br>`com/teenkung/packforge/client/font/FontSelectionRegistry.class` | `client:font.FontProviderSelectionMixin`<br>`client:font.FontSetMixin` |
| `FONT_RELOAD_DIAGNOSTICS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/font/FontOptimizationState.class`<br>`com/teenkung/packforge/client/font/FontReloadDiagnostics.class` | `client:font.FontManagerMixin` |
| `IMMEDIATELY_FAST_FONT_ATLAS_COMPAT` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/compat/ImmediatelyFastFontAtlasCompat.class` | `client-any:compat.ShaderManagerCompatMixin`<br>`client-any:options.OptionsMixin` |
| `LOADER_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/LoaderTimings.class` | `main-any:observe.ForgeLegacyReloadableResourceManagerMixin`<br>`main-any:observe.ReloadableResourceManagerMixin` |
| `LOADING_FADE_CONTROL` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadStatus.class` | `client:ui.LoadingOverlayMixin` |
| `LOADING_STATUS_OVERLAY` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadStatus.class` | `client:ui.LoadingOverlayMixin` |
| `MODEL_PARSE_BATCHING` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/concurrent/CoalescingExecutor.class`<br>`com/teenkung/packforge/concurrent/ModelSchedulingPlan.class` | `client:model.ModelManagerMixin` |
| `MODEL_UV_TRANSPARENCY_CLAMP` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/client/atlas/SpriteCap.class` | `client:model.FaceBakeryMixin` |
| `RELOAD_LISTENER_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadListenerTelemetry.class` | `main:observe.SimpleReloadInstanceMixin` |
| `RELOAD_SUMMARY_TOAST` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | None | `client:ui.LoadingOverlayToastMixin` |
| `RESOURCE_PACK_INDEX` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/PackIndex.class`<br>`com/teenkung/packforge/loader/PackIndexCache.class` | `main:loader.FilePackResourcesMixin` |
| `SHADER_STALL_DIAGNOSTICS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ReloadListenerTelemetry.class` | `main:observe.SimpleReloadInstanceMixin` |
| `STARTUP_EXECUTOR_TUNING` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupExecutorTuner.class` | `main:startup.UtilExecutorMixin` |
| `STARTUP_OPTIMIZER` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupEarlyConfig.class` | `client:startup.MainStartupMixin`<br>`client:startup.MinecraftStartupMixin`<br>`main:startup.UtilExecutorMixin` |
| `STARTUP_STATUS_OVERLAY` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupStatus.class` | `client:startup.GuiStartupMixin` |
| `STARTUP_TIMINGS` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/startup/StartupTimings.class` | `client:startup.MainStartupMixin`<br>`client:startup.MinecraftStartupMixin` |
| `ZIP_READ_POOL` | `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java` | `com/teenkung/packforge/loader/ZipFilePools.class`<br>`com/teenkung/packforge/loader/ZipReadPool.class` | `main:loader.FilePackResourcesMixin` |
