package com.teenkung.packforge.config;

import java.util.List;

/**
 * Backward-compatible live policy facade.
 *
 * <p>Reload and startup lifecycles should use one {@link FeaturePolicy}
 * instance; these methods remain for existing call sites that need a current
 * point-in-time answer.</p>
 */
public final class FeatureFlags {
	public static boolean reloadOptimizerEnabled() { return current().reloadOptimizerEnabled(); }
	public static boolean largeAtlasFixerEnabled() { return current().largeAtlasFixerEnabled(); }
	public static boolean loaderIndexEnabled() { return current().loaderIndexEnabled(); }
	public static boolean loaderZipPoolEnabled() { return current().loaderZipPoolEnabled(); }
	public static boolean loaderTimingsEnabled() { return current().loaderTimingsEnabled(); }
	public static boolean reloadListenerTimingsEnabled() { return current().reloadListenerTimingsEnabled(); }
	public static boolean shaderApplyStallDiagnosticsEnabled() { return current().shaderApplyStallDiagnosticsEnabled(); }
	public static boolean immediatelyFastFontAtlasCompatEnabled() { return current().immediatelyFastFontAtlasCompatEnabled(); }
	public static boolean loadingStatusOverlayEnabled() { return current().loadingStatusOverlayEnabled(); }
	public static boolean loadingScreenFadeOutDisabled() { return current().loadingScreenFadeOutDisabled(); }
	public static boolean reloadSummaryToastEnabled() { return current().reloadSummaryToastEnabled(); }
	public static boolean modelUvTransparencyClampEnabled() { return current().modelUvTransparencyClampEnabled(); }
	public static boolean fontReloadDiagnosticsEnabled() { return current().fontReloadDiagnosticsEnabled(); }
	public static boolean fontPrepareProviderSelectionEnabled() { return current().fontPrepareProviderSelectionEnabled(); }
	public static boolean fontBitmapProviderCacheEnabled() { return current().fontBitmapProviderCacheEnabled(); }
	public static boolean atlasPhaseTimingsEnabled() { return current().atlasPhaseTimingsEnabled(); }
	public static boolean atlasMipParallelEnabled() { return current().atlasMipParallelEnabled(); }
	public static int atlasMipBatchSize() { return current().atlasMipBatchSize(); }
	public static boolean atlasDecodeBatchingEnabled() { return current().atlasDecodeBatchingEnabled(); }
	public static int atlasDecodeBatchSize() { return current().atlasDecodeBatchSize(); }
	public static boolean modelParseBatchingEnabled() { return current().modelParseBatchingEnabled(); }
	public static int modelParseBatchSize() { return current().modelParseBatchSize(); }
	public static boolean modelParseTimingEnabled() { return current().modelParseTimingEnabled(); }
	public static boolean modelAdaptiveBatchingEnabled() { return current().modelAdaptiveBatchingEnabled(); }
	public static boolean modelDuplicateParseCacheEnabled() { return current().modelDuplicateParseCacheEnabled(); }
	public static boolean atlasCapEnabled() { return current().atlasCapEnabled(); }
	public static int atlasCapPx() { return current().atlasCapPx(); }
	public static boolean atlasRetryEnabled() { return current().atlasRetryEnabled(); }
	public static int atlasRetryMaxAttempts() { return current().atlasRetryMaxAttempts(); }
	public static boolean atlasExcludes(String atlasId) { return current().atlasExcludes(atlasId); }
	public static List<String> atlasExclusionIds() { return current().atlasExclusionIds(); }
	public static boolean experimentalAtlasSplitConfigured() { return current().experimentalAtlasSplitConfigured(); }
	public static boolean atlasSplitFallbackToDownscale() { return current().atlasSplitFallbackToDownscale(); }
	public static boolean atlasSplitDiagnostics() { return current().atlasSplitDiagnostics(); }
	public static boolean startupOptimizerEnabled() { return current().startupOptimizerEnabled(); }
	public static boolean startupTimingsEnabled() { return current().startupTimingsEnabled(); }
	public static boolean startupStatusOverlayEnabled() { return current().startupStatusOverlayEnabled(); }
	public static boolean startupExecutorTuningEnabled() { return current().startupExecutorTuningEnabled(); }
	public static int startupWorkerThreads() { return current().startupWorkerThreads(); }
	public static int startupThreadPriority() { return current().startupThreadPriority(); }
	public static boolean startupSkipWithSmoothBoot() { return current().startupSkipWithSmoothBoot(); }
	public static boolean startupAsyncDataParsingEnabled() { return current().startupAsyncDataParsingEnabled(); }
	public static boolean startupAsyncClassScanEnabled() { return current().startupAsyncClassScanEnabled(); }
	public static boolean startupAsyncFontAtlasEnabled() { return current().startupAsyncFontAtlasEnabled(); }

	private static FeaturePolicy current() {
		return FeaturePolicy.current();
	}

	private FeatureFlags() {}
}
