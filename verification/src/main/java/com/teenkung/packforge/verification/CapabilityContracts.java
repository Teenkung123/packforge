package com.teenkung.packforge.verification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CapabilityContracts {
    record Contract(List<String> classes, List<String> anyClasses, List<String> mainMixins,
                    List<String> clientMixins, List<String> anyClientMixins) {}

    static final Map<String, Contract> ALL = contracts();

    private static Map<String, Contract> contracts() {
        Map<String, Contract> result = new LinkedHashMap<>();
        add(result, "RESOURCE_PACK_INDEX", "loader/PackIndex loader/PackIndexCache", "", "loader.FilePackResourcesMixin", "", "");
        add(result, "ZIP_READ_POOL", "loader/ZipFilePools loader/ZipReadPool", "", "loader.FilePackResourcesMixin", "", "");
        add(result, "LOADER_TIMINGS", "loader/LoaderTimings", "", "observe.ReloadableResourceManagerMixin", "", "");
        add(result, "RELOAD_LISTENER_TIMINGS SHADER_STALL_DIAGNOSTICS", "loader/ReloadListenerTelemetry", "", "observe.SimpleReloadInstanceMixin", "", "");
        add(result, "LOADING_STATUS_OVERLAY LOADING_FADE_CONTROL", "loader/ReloadStatus", "", "", "ui.LoadingOverlayMixin", "");
        add(result, "RELOAD_SUMMARY_TOAST", "", "", "", "ui.LoadingOverlayMixin", "");
        add(result, "IMMEDIATELY_FAST_FONT_ATLAS_COMPAT", "client/compat/ImmediatelyFastFontAtlasCompat", "", "", "", "compat.ShaderManagerCompatMixin options.OptionsMixin");
        add(result, "FONT_RELOAD_DIAGNOSTICS", "", "client/font/FontReloadDiagnostics client/font/FontOptimizationState", "", "font.FontManagerMixin", "");
        add(result, "FONT_PROVIDER_PRESELECTION", "", "client/font/FontSelectionRegistry client/font/FontOptimizationState", "", "font.FontManagerMixin font.FontSetMixin", "");
        add(result, "FONT_BITMAP_CACHE", "client/font/FontBitmapProviderCache", "", "", "font.BitmapProviderDefinitionMixin", "");
        add(result, "MODEL_PARSE_BATCHING MODEL_ADAPTIVE_BATCHING MODEL_DUPLICATE_CACHE", "client/model/ModelBatchPlan client/model/ModelParseOptimizer", "", "", "model.ModelManagerMixin", "");
        add(result, "MODEL_PARSE_TIMINGS", "client/model/ModelParseTimings client/model/ModelParseOptimizer", "", "", "model.ModelManagerMixin", "");
        add(result, "ATLAS_PHASE_TIMINGS", "client/atlas/AtlasTimings", "", "", "atlas.SpriteLoaderMixin atlas.TextureAtlasMixin", "");
        add(result, "ATLAS_DECODE_BATCHING ATLAS_MIP_PARALLEL", "client/atlas/AtlasTimings", "", "", "atlas.SpriteLoaderMixin", "");
        add(result, "ATLAS_CAP", "client/atlas/SpriteCap client/atlas/CappedSpriteResourceLoader", "", "", "atlas.SpriteLoaderMixin atlas.TextureAtlasMixin", "");
        add(result, "ATLAS_RETRY", "client/atlas/AtlasRetry", "", "", "atlas.SpriteLoaderMixin", "");
        add(result, "MODEL_UV_TRANSPARENCY_CLAMP", "client/atlas/SpriteCap", "", "", "model.FaceBakeryMixin", "");
        add(result, "STARTUP_OPTIMIZER", "startup/StartupEarlyConfig", "", "startup.UtilExecutorMixin", "startup.MainStartupMixin startup.MinecraftStartupMixin", "");
        add(result, "STARTUP_ASYNC_CLASS_SCAN", "startup/StartupAsyncFeatures", "", "", "startup.MainStartupMixin", "");
        add(result, "STARTUP_ASYNC_DATA STARTUP_ASYNC_FONT_ATLAS", "startup/StartupAsyncFeatures", "", "", "startup.MinecraftStartupMixin", "");
        add(result, "STARTUP_EXECUTOR_TUNING", "startup/StartupExecutorTuner", "", "startup.UtilExecutorMixin", "", "");
        add(result, "STARTUP_STATUS_OVERLAY", "startup/StartupStatus", "", "", "startup.GuiStartupMixin", "");
        add(result, "STARTUP_TIMINGS", "startup/StartupTimings", "", "", "startup.MainStartupMixin startup.MinecraftStartupMixin", "");
        return Map.copyOf(result);
    }

    private static List<String> words(String value) {
        return value.isEmpty() ? List.of() : List.of(value.split(" "));
    }

    private static List<String> classes(String value) {
        return words(value).stream().map(s -> "com/teenkung/packforge/" + s + ".class").toList();
    }

    private static void add(Map<String, Contract> result, String names, String classes, String anyClasses,
                            String main, String client, String anyClient) {
        Contract contract = new Contract(classes(classes), classes(anyClasses), words(main), words(client), words(anyClient));
        for (String name : words(names)) result.put(name, contract);
    }
}
