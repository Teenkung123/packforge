package com.teenkung.packforge.config;

import com.teenkung.packforge.startup.StartupTimings;

import java.util.Set;

/**
 * Immutable reload-scoped view of configuration and capability decisions.
 *
 * <p>Reload work must retain this object instead of consulting mutable config
 * while it is in flight.  A changed config therefore takes effect at the next
 * reload boundary, never halfway through an existing reload.</p>
 */
public record ReloadFeatureSnapshot(
	boolean reloadOptimizerEnabled,
	boolean largeAtlasFixerEnabled,
	boolean loaderIndexEnabled,
	boolean loaderZipPoolEnabled,
	boolean loaderTimingsEnabled,
	boolean reloadListenerTimingsEnabled,
	boolean shaderApplyStallDiagnosticsEnabled,
	boolean loadingStatusOverlayEnabled,
	boolean loadingScreenFadeOutDisabled,
	boolean reloadSummaryToastEnabled,
	boolean immediatelyFastFontAtlasCompatEnabled,
	boolean modelUvTransparencyClampEnabled,
	boolean modelParseBatchingEnabled,
	int modelParseBatchSize,
	boolean modelParseTimingEnabled,
	boolean modelAdaptiveBatchingEnabled,
	boolean modelDuplicateParseCacheEnabled,
	boolean fontReloadDiagnosticsEnabled,
	boolean fontPrepareProviderSelectionEnabled,
	boolean fontBitmapProviderCacheEnabled,
	boolean atlasPhaseTimingsEnabled,
	boolean atlasDecodeBatchingEnabled,
	int atlasDecodeBatchSize,
	boolean atlasMipParallelEnabled,
	int atlasMipBatchSize,
	boolean atlasCapEnabled,
	int atlasCapPx,
	Set<String> atlasExclusionIds,
	boolean atlasRetryEnabled,
	int atlasRetryMaxAttempts,
	boolean experimentalAtlasSplitConfigured,
	boolean atlasSplitFallbackToDownscale,
	boolean atlasSplitDiagnostics,
	boolean startupOptimizerEnabled,
	boolean startupTimingsEnabled,
	boolean startupStatusOverlayEnabled,
	boolean startupExecutorTuningEnabled,
	int startupWorkerThreads,
	int startupThreadPriority,
	boolean startupSkipWithSmoothBoot,
	boolean startupAsyncDataParsingEnabled,
	boolean startupAsyncClassScanEnabled,
	boolean startupAsyncFontAtlasEnabled,
	boolean startupTimingActiveAtStart,
	int workerBudget
) {
	private static final int MAX_WORKER_BUDGET = 32;

	public ReloadFeatureSnapshot {
		atlasExclusionIds = atlasExclusionIds == null ? Set.of() : Set.copyOf(atlasExclusionIds);
		modelParseBatchSize = positive(modelParseBatchSize);
		atlasDecodeBatchSize = positive(atlasDecodeBatchSize);
		atlasMipBatchSize = positive(atlasMipBatchSize);
		atlasCapPx = positive(atlasCapPx);
		atlasRetryMaxAttempts = positive(atlasRetryMaxAttempts);
		startupWorkerThreads = Math.max(0, startupWorkerThreads);
		startupThreadPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, startupThreadPriority));
		workerBudget = boundedWorkerBudget(workerBudget, Runtime.getRuntime().availableProcessors());
	}

	public static ReloadFeatureSnapshot capture() {
		boolean reloadOptimizer = FeatureFlags.reloadOptimizerEnabled();
		boolean largeAtlasFixer = FeatureFlags.largeAtlasFixerEnabled();
		boolean loaderIndex = FeatureFlags.loaderIndexEnabled();
		boolean loaderZipPool = FeatureFlags.loaderZipPoolEnabled();
		boolean loaderTimings = FeatureFlags.loaderTimingsEnabled();
		boolean listenerTimings = FeatureFlags.reloadListenerTimingsEnabled();
		boolean shaderStallDiagnostics = FeatureFlags.shaderApplyStallDiagnosticsEnabled();
		boolean loadingOverlay = FeatureFlags.loadingStatusOverlayEnabled();
		boolean fadeDisabled = FeatureFlags.loadingScreenFadeOutDisabled();
		boolean summaryToast = FeatureFlags.reloadSummaryToastEnabled();
		boolean immediatelyFastFont = FeatureFlags.immediatelyFastFontAtlasCompatEnabled();
		boolean modelUvClamp = FeatureFlags.modelUvTransparencyClampEnabled();
		boolean modelBatching = FeatureFlags.modelParseBatchingEnabled();
		int modelBatchSize = FeatureFlags.modelParseBatchSize();
		boolean modelTimings = FeatureFlags.modelParseTimingEnabled();
		boolean adaptiveModel = FeatureFlags.modelAdaptiveBatchingEnabled();
		boolean duplicateModelCache = FeatureFlags.modelDuplicateParseCacheEnabled();
		boolean fontDiagnostics = FeatureFlags.fontReloadDiagnosticsEnabled();
		boolean fontSelection = FeatureFlags.fontPrepareProviderSelectionEnabled();
		boolean fontBitmapCache = FeatureFlags.fontBitmapProviderCacheEnabled();
		boolean atlasTimings = FeatureFlags.atlasPhaseTimingsEnabled();
		boolean atlasDecode = FeatureFlags.atlasDecodeBatchingEnabled();
		int atlasDecodeSize = FeatureFlags.atlasDecodeBatchSize();
		boolean atlasMip = FeatureFlags.atlasMipParallelEnabled();
		int atlasMipSize = FeatureFlags.atlasMipBatchSize();
		boolean atlasCap = FeatureFlags.atlasCapEnabled();
		int atlasCapSize = FeatureFlags.atlasCapPx();
		Set<String> atlasExclusions = Set.copyOf(FeatureFlags.atlasExclusionIds());
		boolean atlasRetry = FeatureFlags.atlasRetryEnabled();
		int atlasRetryAttempts = FeatureFlags.atlasRetryMaxAttempts();
		boolean atlasSplit = FeatureFlags.experimentalAtlasSplitConfigured();
		boolean atlasSplitFallback = FeatureFlags.atlasSplitFallbackToDownscale();
		boolean atlasSplitDiagnostics = FeatureFlags.atlasSplitDiagnostics();
		boolean startupOptimizer = FeatureFlags.startupOptimizerEnabled();
		boolean startupTimings = FeatureFlags.startupTimingsEnabled();
		boolean startupStatus = FeatureFlags.startupStatusOverlayEnabled();
		boolean startupExecutor = FeatureFlags.startupExecutorTuningEnabled();
		int startupWorkers = FeatureFlags.startupWorkerThreads();
		int startupPriority = FeatureFlags.startupThreadPriority();
		boolean startupSkipSmoothBoot = FeatureFlags.startupSkipWithSmoothBoot();
		boolean startupData = FeatureFlags.startupAsyncDataParsingEnabled();
		boolean startupClassScan = FeatureFlags.startupAsyncClassScanEnabled();
		boolean startupFontAtlas = FeatureFlags.startupAsyncFontAtlasEnabled();
		return new ReloadFeatureSnapshot(
			reloadOptimizer,
			largeAtlasFixer,
			loaderIndex,
			loaderZipPool,
			loaderTimings,
			listenerTimings,
			shaderStallDiagnostics,
			loadingOverlay,
			fadeDisabled,
			summaryToast,
			immediatelyFastFont,
			modelUvClamp,
			modelBatching,
			modelBatchSize,
			modelTimings,
			adaptiveModel,
			duplicateModelCache,
			fontDiagnostics,
			fontSelection,
			fontBitmapCache,
			atlasTimings,
			atlasDecode,
			atlasDecodeSize,
			atlasMip,
			atlasMipSize,
			atlasCap,
			atlasCapSize,
			atlasExclusions,
			atlasRetry,
			atlasRetryAttempts,
			atlasSplit,
			atlasSplitFallback,
			atlasSplitDiagnostics,
			startupOptimizer,
			startupTimings,
			startupStatus,
			startupExecutor,
			startupWorkers,
			startupPriority,
			startupSkipSmoothBoot,
			startupData,
			startupClassScan,
			startupFontAtlas,
			startupTimings && StartupTimings.isActive(),
			boundedWorkerBudget(startupWorkers, Runtime.getRuntime().availableProcessors())
		);
	}

	public boolean statusTrackingEnabled() {
		return loadingStatusOverlayEnabled || startupStatusOverlayEnabled || reloadSummaryToastEnabled;
	}

	public boolean detailedTaskTelemetryEnabled() {
		return reloadListenerTimingsEnabled || startupTimingActiveAtStart;
	}

	public boolean taskExecutorWrappingEnabled() {
		return detailedTaskTelemetryEnabled();
	}

	public boolean atlasExcluded(String atlasId) {
		return atlasId != null && atlasExclusionIds.contains(atlasId);
	}

	public Set<String> capExclusions() {
		return atlasExclusionIds;
	}

	public static int boundedWorkerBudget(int configuredWorkers, int availableProcessors) {
		int fallback = Math.max(1, availableProcessors);
		int requested = configuredWorkers > 0 ? configuredWorkers : fallback;
		return Math.max(1, Math.min(MAX_WORKER_BUDGET, requested));
	}

	private static int positive(int value) {
		return Math.max(1, value);
	}
}
