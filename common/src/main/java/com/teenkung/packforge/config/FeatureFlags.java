package com.teenkung.packforge.config;

public final class FeatureFlags {
	public static boolean reloadOptimizerEnabled() { return PackForgeConfig.get().reloadOptimizerEnabled; }
	public static boolean largeAtlasFixerEnabled() { return PackForgeConfig.get().largeAtlasFixerEnabled; }
	public static boolean loaderIndexEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loaderIndexEnabled; }
	public static boolean loaderZipPoolEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loaderZipPoolEnabled; }
	public static boolean loaderTimingsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loaderTimingsEnabled; }
	public static boolean reloadListenerTimingsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().reloadListenerTimingsEnabled; }
	public static boolean shaderApplyStallDiagnosticsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().shaderApplyStallDiagnosticsEnabled; }
	public static boolean immediatelyFastFontAtlasCompatEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().immediatelyFastFontAtlasCompatEnabled; }
	public static boolean loadingStatusOverlayEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loadingStatusOverlayEnabled; }
	public static boolean loadingScreenFadeOutDisabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loadingScreenFadeOutDisabled; }
	public static boolean reloadSummaryToastEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().reloadSummaryToastEnabled; }
	public static boolean modelUvTransparencyClampEnabled() { return largeAtlasFixerEnabled() && PackForgeConfig.get().modelUvTransparencyClampEnabled; }
	public static boolean fontReloadDiagnosticsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().fontReloadDiagnosticsEnabled; }
	public static boolean fontPrepareProviderSelectionEnabled() { return (reloadOptimizerEnabled() && PackForgeConfig.get().fontPrepareProviderSelectionEnabled) || startupAsyncFontAtlasEnabled(); }
	public static boolean fontBitmapProviderCacheEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().fontBitmapProviderCacheEnabled; }
	public static boolean atlasPhaseTimingsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().atlasPhaseTimingsEnabled; }
	public static boolean atlasMipParallelEnabled() { return (largeAtlasFixerEnabled() && PackForgeConfig.get().atlasMipParallelEnabled) || startupAsyncFontAtlasEnabled(); }
	public static int atlasMipBatchSize() { return PackForgeConfig.get().atlasMipBatchSize; }
	public static boolean atlasDecodeBatchingEnabled() { return (reloadOptimizerEnabled() && PackForgeConfig.get().atlasDecodeBatchingEnabled) || startupAsyncFontAtlasEnabled(); }
	public static int atlasDecodeBatchSize() { return PackForgeConfig.get().atlasDecodeBatchSize; }
	public static boolean modelParseBatchingEnabled() { return (reloadOptimizerEnabled() && PackForgeConfig.get().modelParseBatchingEnabled) || startupAsyncDataParsingEnabled(); }
	public static int modelParseBatchSize() { return PackForgeConfig.get().modelParseBatchSize; }
	public static boolean modelParseTimingEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().modelParseTimingEnabled; }
	public static boolean modelAdaptiveBatchingEnabled() { return (reloadOptimizerEnabled() && PackForgeConfig.get().modelAdaptiveBatchingEnabled) || startupAsyncDataParsingEnabled(); }
	public static boolean modelDuplicateParseCacheEnabled() { return (reloadOptimizerEnabled() && PackForgeConfig.get().modelDuplicateParseCacheEnabled) || startupAsyncDataParsingEnabled(); }
	public static boolean atlasCapEnabled() { return largeAtlasFixerEnabled() && PackForgeConfig.get().atlasCapEnabled; }
	public static int atlasCapPx() { return PackForgeConfig.get().atlasCapPx; }
	public static boolean atlasRetryEnabled() { return largeAtlasFixerEnabled() && PackForgeConfig.get().atlasRetryEnabled; }
	public static int atlasRetryMaxAttempts() { return PackForgeConfig.get().atlasRetryMaxAttempts; }
	public static boolean atlasExcludes(String atlasId) { return PackForgeConfig.get().atlasExcludeIds.contains(atlasId); }
	public static boolean experimentalAtlasSplitConfigured() { return largeAtlasFixerEnabled() && PackForgeConfig.get().experimentalAtlasSplit; }
	public static boolean atlasSplitFallbackToDownscale() { return PackForgeConfig.get().atlasSplitFallbackToDownscale; }
	public static boolean atlasSplitDiagnostics() { return PackForgeConfig.get().atlasSplitDiagnostics; }
	public static boolean startupOptimizerEnabled() { return PackForgeConfig.get().startupOptimizerEnabled; }
	public static boolean startupTimingsEnabled() { return startupOptimizerEnabled() && PackForgeConfig.get().startupTimingsEnabled; }
	public static boolean startupStatusOverlayEnabled() { return startupOptimizerEnabled() && PackForgeConfig.get().startupStatusOverlayEnabled; }
	public static boolean startupExecutorTuningEnabled() { return startupOptimizerEnabled() && PackForgeConfig.get().startupExecutorTuningEnabled; }
	public static int startupWorkerThreads() { return PackForgeConfig.get().startupWorkerThreads; }
	public static int startupThreadPriority() { return PackForgeConfig.get().startupThreadPriority; }
	public static boolean startupSkipWithSmoothBoot() { return PackForgeConfig.get().startupSkipWithSmoothBoot; }
	public static boolean startupAsyncDataParsingEnabled() { return startupOptimizerEnabled() && PackForgeConfig.get().startupAsyncDataParsingEnabled; }
	public static boolean startupAsyncClassScanEnabled() { return startupOptimizerEnabled() && PackForgeConfig.get().startupAsyncClassScanEnabled; }
	public static boolean startupAsyncFontAtlasEnabled() { return startupOptimizerEnabled() && PackForgeConfig.get().startupAsyncFontAtlasEnabled; }

	private FeatureFlags() {}
}
