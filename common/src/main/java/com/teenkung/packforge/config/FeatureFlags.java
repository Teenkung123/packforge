package com.teenkung.packforge.config;

import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_CAP;
import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_DECODE_BATCHING;
import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_MIP_PARALLEL;
import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_PHASE_TIMINGS;
import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_RETRY;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_BITMAP_CACHE;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_PROVIDER_PRESELECTION;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_RELOAD_DIAGNOSTICS;
import static com.teenkung.packforge.config.PackForgeCapability.IMMEDIATELY_FAST_FONT_ATLAS_COMPAT;
import static com.teenkung.packforge.config.PackForgeCapability.LOADER_TIMINGS;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_FADE_CONTROL;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_STATUS_OVERLAY;
import static com.teenkung.packforge.config.PackForgeCapability.MODEL_ADAPTIVE_BATCHING;
import static com.teenkung.packforge.config.PackForgeCapability.MODEL_DUPLICATE_CACHE;
import static com.teenkung.packforge.config.PackForgeCapability.MODEL_PARSE_BATCHING;
import static com.teenkung.packforge.config.PackForgeCapability.MODEL_PARSE_TIMINGS;
import static com.teenkung.packforge.config.PackForgeCapability.MODEL_UV_TRANSPARENCY_CLAMP;
import static com.teenkung.packforge.config.PackForgeCapability.RELOAD_LISTENER_TIMINGS;
import static com.teenkung.packforge.config.PackForgeCapability.RELOAD_SUMMARY_TOAST;
import static com.teenkung.packforge.config.PackForgeCapability.RESOURCE_PACK_INDEX;
import static com.teenkung.packforge.config.PackForgeCapability.RESOURCE_READ_REUSE;
import static com.teenkung.packforge.config.PackForgeCapability.SHADER_STALL_DIAGNOSTICS;
import static com.teenkung.packforge.config.PackForgeCapability.STARTUP_ASYNC_CLASS_SCAN;
import static com.teenkung.packforge.config.PackForgeCapability.STARTUP_ASYNC_DATA;
import static com.teenkung.packforge.config.PackForgeCapability.STARTUP_ASYNC_FONT_ATLAS;
import static com.teenkung.packforge.config.PackForgeCapability.STARTUP_EXECUTOR_TUNING;
import static com.teenkung.packforge.config.PackForgeCapability.STARTUP_OPTIMIZER;
import static com.teenkung.packforge.config.PackForgeCapability.STARTUP_STATUS_OVERLAY;
import static com.teenkung.packforge.config.PackForgeCapability.STARTUP_TIMINGS;
import static com.teenkung.packforge.config.PackForgeCapability.ZIP_READ_POOL;

import java.util.List;

public final class FeatureFlags {
	public static boolean reloadOptimizerEnabled() { return PackForgeConfig.get().reloadOptimizerEnabled; }
	public static boolean resourceReadReuseEnabled() { return OptimizationPlan.enabledNow(RESOURCE_READ_REUSE); }
	public static int optimizationMemoryMiB() { return Math.max(1, Math.min(128, PackForgeConfig.get().optimizationMemoryMiB)); }
	public static boolean largeAtlasFixerEnabled() { return PackForgeConfig.get().largeAtlasFixerEnabled; }
	public static boolean loaderIndexEnabled() { return OptimizationPlan.enabledNow(RESOURCE_PACK_INDEX); }
	public static boolean loaderZipPoolEnabled() { return OptimizationPlan.enabledNow(ZIP_READ_POOL); }
	// Keep the measurement path available when the optimizer is disabled so the
	// release benchmark can compare the same reload instrumentation in both modes.
	public static boolean loaderTimingsEnabled() { return OptimizationPlan.enabledNow(LOADER_TIMINGS); }
	public static boolean reloadListenerTimingsEnabled() { return OptimizationPlan.enabledNow(RELOAD_LISTENER_TIMINGS); }
	public static boolean shaderApplyStallDiagnosticsEnabled() { return OptimizationPlan.enabledNow(SHADER_STALL_DIAGNOSTICS); }
	public static boolean immediatelyFastFontAtlasCompatEnabled() { return OptimizationPlan.enabledNow(IMMEDIATELY_FAST_FONT_ATLAS_COMPAT); }
	public static boolean loadingStatusOverlayEnabled() { return OptimizationPlan.enabledNow(LOADING_STATUS_OVERLAY); }
	public static boolean loadingScreenFadeOutDisabled() { return OptimizationPlan.enabledNow(LOADING_FADE_CONTROL); }
	public static boolean reloadSummaryToastEnabled() { return OptimizationPlan.enabledNow(RELOAD_SUMMARY_TOAST); }
	public static boolean modelUvTransparencyClampEnabled() { return OptimizationPlan.enabledNow(MODEL_UV_TRANSPARENCY_CLAMP); }
	public static boolean fontReloadDiagnosticsEnabled() { return OptimizationPlan.enabledNow(FONT_RELOAD_DIAGNOSTICS); }
	public static boolean fontPrepareProviderSelectionEnabled() { return OptimizationPlan.enabledNow(FONT_PROVIDER_PRESELECTION); }
	public static boolean fontBitmapProviderCacheEnabled() { return OptimizationPlan.enabledNow(FONT_BITMAP_CACHE); }
	public static boolean atlasPhaseTimingsEnabled() { return OptimizationPlan.enabledNow(ATLAS_PHASE_TIMINGS); }
	public static boolean atlasMipParallelEnabled() { return OptimizationPlan.enabledNow(ATLAS_MIP_PARALLEL); }
	public static int atlasMipBatchSize() { return PackForgeConfig.get().atlasMipBatchSize; }
	public static boolean atlasDecodeBatchingEnabled() { return OptimizationPlan.enabledNow(ATLAS_DECODE_BATCHING); }
	public static int atlasDecodeBatchSize() { return PackForgeConfig.get().atlasDecodeBatchSize; }
	public static boolean modelParseBatchingEnabled() { return OptimizationPlan.enabledNow(MODEL_PARSE_BATCHING); }
	public static int modelParseBatchSize() { return PackForgeConfig.get().modelParseBatchSize; }
	public static boolean modelParseTimingEnabled() { return OptimizationPlan.enabledNow(MODEL_PARSE_TIMINGS); }
	public static boolean modelAdaptiveBatchingEnabled() { return OptimizationPlan.enabledNow(MODEL_ADAPTIVE_BATCHING); }
	public static boolean modelDuplicateParseCacheEnabled() { return OptimizationPlan.enabledNow(MODEL_DUPLICATE_CACHE); }
	public static boolean atlasCapEnabled() { return OptimizationPlan.enabledNow(ATLAS_CAP); }
	public static int atlasCapPx() { return PackForgeConfig.get().atlasCapPx; }
	public static boolean atlasRetryEnabled() { return OptimizationPlan.enabledNow(ATLAS_RETRY); }
	public static int atlasRetryMaxAttempts() { return PackForgeConfig.get().atlasRetryMaxAttempts; }
	public static boolean atlasExcludes(String atlasId) { return PackForgeConfig.get().atlasExcludeIds.contains(atlasId); }
	public static List<String> atlasExclusionIds() {
		List<String> exclusions = PackForgeConfig.get().atlasExcludeIds;
		return exclusions == null ? List.of() : List.copyOf(exclusions);
	}

	// Reserved settings remain serialized for compatibility but are not delivered capabilities.
	public static boolean experimentalAtlasSplitConfigured() { return false; }
	public static boolean atlasSplitFallbackToDownscale() { return false; }
	public static boolean atlasSplitDiagnostics() { return false; }

	public static boolean startupOptimizerEnabled() { return OptimizationPlan.enabledNow(STARTUP_OPTIMIZER); }
	public static boolean startupTimingsEnabled() { return OptimizationPlan.enabledNow(STARTUP_TIMINGS); }
	public static boolean startupStatusOverlayEnabled() { return OptimizationPlan.enabledNow(STARTUP_STATUS_OVERLAY); }
	public static boolean startupExecutorTuningEnabled() { return OptimizationPlan.enabledNow(STARTUP_EXECUTOR_TUNING); }
	public static int startupWorkerThreads() { return PackForgeConfig.get().startupWorkerThreads; }
	public static int startupThreadPriority() { return PackForgeConfig.get().startupThreadPriority; }
	public static boolean startupSkipWithSmoothBoot() { return PackForgeConfig.get().startupSkipWithSmoothBoot; }
	public static boolean startupAsyncDataParsingEnabled() { return OptimizationPlan.enabledNow(STARTUP_ASYNC_DATA); }
	public static boolean startupAsyncClassScanEnabled() { return OptimizationPlan.enabledNow(STARTUP_ASYNC_CLASS_SCAN); }
	public static boolean startupAsyncFontAtlasEnabled() { return OptimizationPlan.enabledNow(STARTUP_ASYNC_FONT_ATLAS); }


	private FeatureFlags() {}
}
