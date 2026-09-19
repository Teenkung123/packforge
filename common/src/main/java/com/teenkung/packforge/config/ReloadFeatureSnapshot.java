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
	int workerBudget,
	boolean resourceReadReuseEnabled,
	int optimizationMemoryMiB,
	OptimizationPlan optimizationPlan
) {
	private static final int MAX_WORKER_BUDGET = 32;

	public ReloadFeatureSnapshot {
		optimizationMemoryMiB = Math.max(1, Math.min(128, optimizationMemoryMiB));
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
		OptimizationPlan plan = OptimizationPlan.capture(PackForgeConfig.get());
		boolean reloadOptimizer = FeatureFlags.reloadOptimizerEnabled();
		boolean largeAtlasFixer = FeatureFlags.largeAtlasFixerEnabled();
		boolean loaderIndex = plan.enabled(PackForgeCapability.RESOURCE_PACK_INDEX);
		boolean loaderZipPool = plan.enabled(PackForgeCapability.ZIP_READ_POOL);
		boolean loaderTimings = plan.enabled(PackForgeCapability.LOADER_TIMINGS);
		boolean listenerTimings = plan.enabled(PackForgeCapability.RELOAD_LISTENER_TIMINGS);
		boolean shaderStallDiagnostics = plan.enabled(PackForgeCapability.SHADER_STALL_DIAGNOSTICS);
		boolean loadingOverlay = plan.enabled(PackForgeCapability.LOADING_STATUS_OVERLAY);
		boolean fadeDisabled = plan.enabled(PackForgeCapability.LOADING_FADE_CONTROL);
		boolean summaryToast = plan.enabled(PackForgeCapability.RELOAD_SUMMARY_TOAST);
		boolean immediatelyFastFont = plan.enabled(PackForgeCapability.IMMEDIATELY_FAST_FONT_ATLAS_COMPAT);
		boolean modelUvClamp = plan.enabled(PackForgeCapability.MODEL_UV_TRANSPARENCY_CLAMP);
		boolean modelBatching = plan.enabled(PackForgeCapability.MODEL_PARSE_BATCHING);
		int modelBatchSize = FeatureFlags.modelParseBatchSize();
		boolean modelTimings = plan.enabled(PackForgeCapability.MODEL_PARSE_TIMINGS);
		boolean adaptiveModel = plan.enabled(PackForgeCapability.MODEL_ADAPTIVE_BATCHING);
		boolean duplicateModelCache = plan.enabled(PackForgeCapability.MODEL_DUPLICATE_CACHE);
		boolean fontDiagnostics = plan.enabled(PackForgeCapability.FONT_RELOAD_DIAGNOSTICS);
		boolean fontSelection = plan.enabled(PackForgeCapability.FONT_PROVIDER_PRESELECTION);
		boolean fontBitmapCache = plan.enabled(PackForgeCapability.FONT_BITMAP_CACHE);
		boolean atlasTimings = plan.enabled(PackForgeCapability.ATLAS_PHASE_TIMINGS);
		boolean atlasDecode = plan.enabled(PackForgeCapability.ATLAS_DECODE_BATCHING);
		int atlasDecodeSize = FeatureFlags.atlasDecodeBatchSize();
		boolean atlasMip = plan.enabled(PackForgeCapability.ATLAS_MIP_PARALLEL);
		int atlasMipSize = FeatureFlags.atlasMipBatchSize();
		boolean atlasCap = plan.enabled(PackForgeCapability.ATLAS_CAP);
		int atlasCapSize = FeatureFlags.atlasCapPx();
		Set<String> atlasExclusions = Set.copyOf(FeatureFlags.atlasExclusionIds());
		boolean atlasRetry = plan.enabled(PackForgeCapability.ATLAS_RETRY);
		int atlasRetryAttempts = FeatureFlags.atlasRetryMaxAttempts();
		boolean atlasSplit = FeatureFlags.experimentalAtlasSplitConfigured();
		boolean atlasSplitFallback = FeatureFlags.atlasSplitFallbackToDownscale();
		boolean atlasSplitDiagnostics = FeatureFlags.atlasSplitDiagnostics();
		boolean startupOptimizer = plan.enabled(PackForgeCapability.STARTUP_OPTIMIZER);
		boolean startupTimings = plan.enabled(PackForgeCapability.STARTUP_TIMINGS);
		boolean startupStatus = plan.enabled(PackForgeCapability.STARTUP_STATUS_OVERLAY);
		boolean startupExecutor = plan.enabled(PackForgeCapability.STARTUP_EXECUTOR_TUNING);
		int startupWorkers = FeatureFlags.startupWorkerThreads();
		int startupPriority = FeatureFlags.startupThreadPriority();
		boolean startupSkipSmoothBoot = FeatureFlags.startupSkipWithSmoothBoot();
		boolean startupData = plan.enabled(PackForgeCapability.STARTUP_ASYNC_DATA);
		boolean startupClassScan = plan.enabled(PackForgeCapability.STARTUP_ASYNC_CLASS_SCAN);
		boolean startupFontAtlas = plan.enabled(PackForgeCapability.STARTUP_ASYNC_FONT_ATLAS);
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
			boundedWorkerBudget(startupWorkers, Runtime.getRuntime().availableProcessors()),
			plan.enabled(PackForgeCapability.RESOURCE_READ_REUSE),
			FeatureFlags.optimizationMemoryMiB(),
			plan
		);
	}

	public boolean statusTrackingEnabled() {
		return loadingStatusOverlayEnabled || startupStatusOverlayEnabled || reloadSummaryToastEnabled || externalFeedbackEnabled();
	}

	public boolean externalFeedbackEnabled() {
		OptimizationPlan.Decision decision = optimizationPlan.decision(PackForgeCapability.LOADING_STATUS_OVERLAY);
		return reloadOptimizerEnabled && decision.requested() && !loadingStatusOverlayEnabled
			&& (decision.owner() == OptimizationPlan.Owner.RRLS || decision.owner() == OptimizationPlan.Owner.QUICK_PACK);
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
