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
	public static boolean largeAtlasFixerEnabled() { return PackForgeConfig.get().largeAtlasFixerEnabled; }
	public static boolean loaderIndexEnabled() { return supports(RESOURCE_PACK_INDEX) && reloadOptimizerEnabled() && PackForgeConfig.get().loaderIndexEnabled; }
	public static boolean loaderZipPoolEnabled() { return supports(ZIP_READ_POOL) && reloadOptimizerEnabled() && PackForgeConfig.get().loaderZipPoolEnabled; }
	// Keep the measurement path available when the optimizer is disabled so the
	// release benchmark can compare the same reload instrumentation in both modes.
	public static boolean loaderTimingsEnabled() { return supports(LOADER_TIMINGS) && PackForgeConfig.get().loaderTimingsEnabled; }
	public static boolean reloadListenerTimingsEnabled() { return supports(RELOAD_LISTENER_TIMINGS) && reloadOptimizerEnabled() && PackForgeConfig.get().reloadListenerTimingsEnabled; }
	public static boolean shaderApplyStallDiagnosticsEnabled() { return supports(SHADER_STALL_DIAGNOSTICS) && reloadOptimizerEnabled() && PackForgeConfig.get().shaderApplyStallDiagnosticsEnabled; }
	public static boolean immediatelyFastFontAtlasCompatEnabled() { return supports(IMMEDIATELY_FAST_FONT_ATLAS_COMPAT) && reloadOptimizerEnabled() && PackForgeConfig.get().immediatelyFastFontAtlasCompatEnabled; }
	public static boolean loadingStatusOverlayEnabled() { return supports(LOADING_STATUS_OVERLAY) && reloadOptimizerEnabled() && PackForgeConfig.get().loadingStatusOverlayEnabled; }
	public static boolean loadingScreenFadeOutDisabled() { return supports(LOADING_FADE_CONTROL) && reloadOptimizerEnabled() && PackForgeConfig.get().loadingScreenFadeOutDisabled; }
	public static boolean reloadSummaryToastEnabled() { return supports(RELOAD_SUMMARY_TOAST) && reloadOptimizerEnabled() && PackForgeConfig.get().reloadSummaryToastEnabled; }
	public static boolean modelUvTransparencyClampEnabled() { return supports(MODEL_UV_TRANSPARENCY_CLAMP) && largeAtlasFixerEnabled() && PackForgeConfig.get().modelUvTransparencyClampEnabled; }
	public static boolean fontReloadDiagnosticsEnabled() { return supports(FONT_RELOAD_DIAGNOSTICS) && reloadOptimizerEnabled() && PackForgeConfig.get().fontReloadDiagnosticsEnabled; }
	public static boolean fontPrepareProviderSelectionEnabled() { return supports(FONT_PROVIDER_PRESELECTION) && ((reloadOptimizerEnabled() && PackForgeConfig.get().fontPrepareProviderSelectionEnabled) || startupAsyncFontAtlasEnabled()); }
	public static boolean fontBitmapProviderCacheEnabled() { return supports(FONT_BITMAP_CACHE) && reloadOptimizerEnabled() && PackForgeConfig.get().fontBitmapProviderCacheEnabled; }
	public static boolean atlasPhaseTimingsEnabled() { return supports(ATLAS_PHASE_TIMINGS) && reloadOptimizerEnabled() && PackForgeConfig.get().atlasPhaseTimingsEnabled; }
	public static boolean atlasMipParallelEnabled() { return supports(ATLAS_MIP_PARALLEL) && ((largeAtlasFixerEnabled() && PackForgeConfig.get().atlasMipParallelEnabled) || startupAsyncFontAtlasEnabled()); }
	public static int atlasMipBatchSize() { return PackForgeConfig.get().atlasMipBatchSize; }
	public static boolean atlasDecodeBatchingEnabled() { return supports(ATLAS_DECODE_BATCHING) && ((reloadOptimizerEnabled() && PackForgeConfig.get().atlasDecodeBatchingEnabled) || startupAsyncFontAtlasEnabled()); }
	public static int atlasDecodeBatchSize() { return PackForgeConfig.get().atlasDecodeBatchSize; }
	public static boolean modelParseBatchingEnabled() { return supports(MODEL_PARSE_BATCHING) && ((reloadOptimizerEnabled() && PackForgeConfig.get().modelParseBatchingEnabled) || startupAsyncDataParsingEnabled()); }
	public static int modelParseBatchSize() { return PackForgeConfig.get().modelParseBatchSize; }
	public static boolean modelParseTimingEnabled() { return supports(MODEL_PARSE_TIMINGS) && reloadOptimizerEnabled() && PackForgeConfig.get().modelParseTimingEnabled; }
	public static boolean modelAdaptiveBatchingEnabled() { return supports(MODEL_ADAPTIVE_BATCHING) && ((reloadOptimizerEnabled() && PackForgeConfig.get().modelAdaptiveBatchingEnabled) || startupAsyncDataParsingEnabled()); }
	public static boolean modelDuplicateParseCacheEnabled() { return supports(MODEL_DUPLICATE_CACHE) && ((reloadOptimizerEnabled() && PackForgeConfig.get().modelDuplicateParseCacheEnabled) || startupAsyncDataParsingEnabled()); }
	public static boolean atlasCapEnabled() { return supports(ATLAS_CAP) && largeAtlasFixerEnabled() && PackForgeConfig.get().atlasCapEnabled; }
	public static int atlasCapPx() { return PackForgeConfig.get().atlasCapPx; }
	public static boolean atlasRetryEnabled() { return supports(ATLAS_RETRY) && largeAtlasFixerEnabled() && PackForgeConfig.get().atlasRetryEnabled; }
	public static int atlasRetryMaxAttempts() { return PackForgeConfig.get().atlasRetryMaxAttempts; }
	public static boolean atlasExcludes(String atlasId) { return PackForgeConfig.get().atlasExcludeIds.contains(atlasId); }
	public static List<String> atlasExclusionIds() {
		List<String> exclusions = PackForgeConfig.get().atlasExcludeIds;
		return exclusions == null ? List.of() : List.copyOf(exclusions);
	}

	// Reserved settings remain serialized in config v12 but are not a delivered capability.
	public static boolean experimentalAtlasSplitConfigured() { return false; }
	public static boolean atlasSplitFallbackToDownscale() { return false; }
	public static boolean atlasSplitDiagnostics() { return false; }

	public static boolean startupOptimizerEnabled() { return supports(STARTUP_OPTIMIZER) && PackForgeConfig.get().startupOptimizerEnabled; }
	public static boolean startupTimingsEnabled() { return supports(STARTUP_TIMINGS) && startupOptimizerEnabled() && PackForgeConfig.get().startupTimingsEnabled; }
	public static boolean startupStatusOverlayEnabled() { return supports(STARTUP_STATUS_OVERLAY) && startupOptimizerEnabled() && PackForgeConfig.get().startupStatusOverlayEnabled; }
	public static boolean startupExecutorTuningEnabled() { return supports(STARTUP_EXECUTOR_TUNING) && startupOptimizerEnabled() && PackForgeConfig.get().startupExecutorTuningEnabled; }
	public static int startupWorkerThreads() { return PackForgeConfig.get().startupWorkerThreads; }
	public static int startupThreadPriority() { return PackForgeConfig.get().startupThreadPriority; }
	public static boolean startupSkipWithSmoothBoot() { return PackForgeConfig.get().startupSkipWithSmoothBoot; }
	public static boolean startupAsyncDataParsingEnabled() { return supports(STARTUP_ASYNC_DATA) && startupOptimizerEnabled() && PackForgeConfig.get().startupAsyncDataParsingEnabled; }
	public static boolean startupAsyncClassScanEnabled() { return supports(STARTUP_ASYNC_CLASS_SCAN) && startupOptimizerEnabled() && PackForgeConfig.get().startupAsyncClassScanEnabled; }
	public static boolean startupAsyncFontAtlasEnabled() { return supports(STARTUP_ASYNC_FONT_ATLAS) && startupOptimizerEnabled() && PackForgeConfig.get().startupAsyncFontAtlasEnabled; }

	private static boolean supports(PackForgeCapability capability) {
		return PackForgeCapabilities.supports(capability);
	}

	private FeatureFlags() {}
}
