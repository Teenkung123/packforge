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
		FeaturePolicy policy = FeaturePolicy.current();
		boolean reloadOptimizer = policy.reloadOptimizerEnabled();
		boolean largeAtlasFixer = policy.largeAtlasFixerEnabled();
		boolean loaderIndex = policy.loaderIndexEnabled();
		boolean loaderZipPool = policy.loaderZipPoolEnabled();
		boolean loaderTimings = policy.loaderTimingsEnabled();
		boolean listenerTimings = policy.reloadListenerTimingsEnabled();
		boolean shaderStallDiagnostics = policy.shaderApplyStallDiagnosticsEnabled();
		boolean loadingOverlay = policy.loadingStatusOverlayEnabled();
		boolean fadeDisabled = policy.loadingScreenFadeOutDisabled();
		boolean summaryToast = policy.reloadSummaryToastEnabled();
		boolean immediatelyFastFont = policy.immediatelyFastFontAtlasCompatEnabled();
		boolean modelUvClamp = policy.modelUvTransparencyClampEnabled();
		boolean modelBatching = policy.modelParseBatchingEnabled();
		int modelBatchSize = policy.modelParseBatchSize();
		boolean modelTimings = policy.modelParseTimingEnabled();
		boolean adaptiveModel = policy.modelAdaptiveBatchingEnabled();
		boolean duplicateModelCache = policy.modelDuplicateParseCacheEnabled();
		boolean fontDiagnostics = policy.fontReloadDiagnosticsEnabled();
		boolean fontSelection = policy.fontPrepareProviderSelectionEnabled();
		boolean fontBitmapCache = policy.fontBitmapProviderCacheEnabled();
		boolean atlasTimings = policy.atlasPhaseTimingsEnabled();
		boolean atlasDecode = policy.atlasDecodeBatchingEnabled();
		int atlasDecodeSize = policy.atlasDecodeBatchSize();
		boolean atlasMip = policy.atlasMipParallelEnabled();
		int atlasMipSize = policy.atlasMipBatchSize();
		boolean atlasCap = policy.atlasCapEnabled();
		int atlasCapSize = policy.atlasCapPx();
		Set<String> atlasExclusions = Set.copyOf(policy.atlasExclusionIds());
		boolean atlasRetry = policy.atlasRetryEnabled();
		int atlasRetryAttempts = policy.atlasRetryMaxAttempts();
		boolean atlasSplit = policy.experimentalAtlasSplitConfigured();
		boolean atlasSplitFallback = policy.atlasSplitFallbackToDownscale();
		boolean atlasSplitDiagnostics = policy.atlasSplitDiagnostics();
		boolean startupOptimizer = policy.startupOptimizerEnabled();
		boolean startupTimings = policy.startupTimingsEnabled();
		boolean startupStatus = policy.startupStatusOverlayEnabled();
		boolean startupExecutor = policy.startupExecutorTuningEnabled();
		int startupWorkers = policy.startupWorkerThreads();
		int startupPriority = policy.startupThreadPriority();
		boolean startupSkipSmoothBoot = policy.startupSkipWithSmoothBoot();
		boolean startupData = policy.startupAsyncDataParsingEnabled();
		boolean startupClassScan = policy.startupAsyncClassScanEnabled();
		boolean startupFontAtlas = policy.startupAsyncFontAtlasEnabled();
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
