package com.teenkung.packforge.config;

import com.teenkung.packforge.platform.PackForgeCompat;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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

/**
 * Immutable capability and configuration decisions for one policy boundary.
 *
 * <p>The generated artifact profile is the only source of capability truth;
 * the copied configuration is the only source of user settings.  Consumers
 * that span a reload should retain one instance instead of rereading either
 * source while work is in flight.</p>
 */
public final class FeaturePolicy {
	private final PackForgeConfig.Cfg config;
	private final PackForgeCapabilityProfile capabilities;
	private final QuickPackCompatibility.Profile quickPack;
	private final boolean shaderPipelinePresent;

	private FeaturePolicy(
		PackForgeConfig.Cfg config,
		PackForgeCapabilityProfile capabilities,
		QuickPackCompatibility.Profile quickPack,
		boolean shaderPipelinePresent
	) {
		this.config = PackForgeConfig.copyOf(Objects.requireNonNull(config, "config"));
		this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
		this.quickPack = Objects.requireNonNull(quickPack, "quickPack");
		this.shaderPipelinePresent = shaderPipelinePresent;
	}

	public static FeaturePolicy current() {
		return new FeaturePolicy(PackForgeConfig.get(), PackForgeCapabilities.profile(), QuickPackCompatibility.current(), PackForgeCompat.isShaderPipelinePresent());
	}

	public static FeaturePolicy forConfiguration(PackForgeConfig.Cfg config) {
		return forConfiguration(config, QuickPackCompatibility.current());
	}

	public static FeaturePolicy forConfiguration(PackForgeConfig.Cfg config, QuickPackCompatibility.Profile quickPack) {
		return forConfiguration(config, quickPack, PackForgeCompat.isShaderPipelinePresent());
	}

	public static FeaturePolicy forConfiguration(
		PackForgeConfig.Cfg config,
		QuickPackCompatibility.Profile quickPack,
		boolean shaderPipelinePresent
	) {
		return new FeaturePolicy(config, PackForgeCapabilities.profile(), quickPack, shaderPipelinePresent);
	}

	static FeaturePolicy forTesting(PackForgeConfig.Cfg config, PackForgeCapabilityProfile capabilities) {
		return forTesting(config, capabilities, QuickPackCompatibility.absentForTesting());
	}

	static FeaturePolicy forTesting(PackForgeConfig.Cfg config, PackForgeCapabilityProfile capabilities, QuickPackCompatibility.Profile quickPack) {
		return forTesting(config, capabilities, quickPack, false);
	}

	static FeaturePolicy forTesting(
		PackForgeConfig.Cfg config,
		PackForgeCapabilityProfile capabilities,
		QuickPackCompatibility.Profile quickPack,
		boolean shaderPipelinePresent
	) {
		return new FeaturePolicy(config, capabilities, quickPack, shaderPipelinePresent);
	}

	public boolean reloadOptimizerEnabled() { return config.reloadOptimizerEnabled; }
	public boolean largeAtlasFixerEnabled() { return config.largeAtlasFixerEnabled; }
	public boolean loaderIndexEnabled() { return enabled(RESOURCE_PACK_INDEX, reloadOptimizerEnabled(), config.loaderIndexEnabled); }
	public boolean loaderZipPoolEnabled() { return enabled(ZIP_READ_POOL, reloadOptimizerEnabled(), config.loaderZipPoolEnabled); }
	public boolean loaderTimingsEnabled() { return enabled(LOADER_TIMINGS, true, config.loaderTimingsEnabled); }
	public boolean reloadListenerTimingsEnabled() { return enabled(RELOAD_LISTENER_TIMINGS, reloadOptimizerEnabled(), config.reloadListenerTimingsEnabled); }
	public boolean shaderApplyStallDiagnosticsEnabled() { return enabled(SHADER_STALL_DIAGNOSTICS, reloadOptimizerEnabled(), config.shaderApplyStallDiagnosticsEnabled); }
	public boolean immediatelyFastFontAtlasCompatEnabled() { return enabled(IMMEDIATELY_FAST_FONT_ATLAS_COMPAT, reloadOptimizerEnabled(), config.immediatelyFastFontAtlasCompatEnabled); }
	public boolean loadingStatusOverlayEnabled() { return enabled(LOADING_STATUS_OVERLAY, reloadOptimizerEnabled(), config.loadingStatusOverlayEnabled); }
	public boolean loadingScreenFadeOutDisabled() { return enabled(LOADING_FADE_CONTROL, reloadOptimizerEnabled(), config.loadingScreenFadeOutDisabled); }
	public boolean reloadSummaryToastEnabled() { return enabled(RELOAD_SUMMARY_TOAST, reloadOptimizerEnabled(), config.reloadSummaryToastEnabled); }
	public boolean modelUvTransparencyClampEnabled() { return enabled(MODEL_UV_TRANSPARENCY_CLAMP, largeAtlasFixerEnabled(), config.modelUvTransparencyClampEnabled); }
	public boolean fontReloadDiagnosticsEnabled() { return enabled(FONT_RELOAD_DIAGNOSTICS, reloadOptimizerEnabled(), config.fontReloadDiagnosticsEnabled); }
	public boolean fontPrepareProviderSelectionEnabled() {
		return supports(FONT_PROVIDER_PRESELECTION) && !quickPack.owns(FONT_PROVIDER_PRESELECTION)
			&& ((reloadOptimizerEnabled() && config.fontPrepareProviderSelectionEnabled) || startupAsyncFontAtlasEnabled());
	}
	public boolean fontBitmapProviderCacheEnabled() { return enabled(FONT_BITMAP_CACHE, reloadOptimizerEnabled(), config.fontBitmapProviderCacheEnabled); }
	public boolean atlasPhaseTimingsEnabled() { return enabled(ATLAS_PHASE_TIMINGS, reloadOptimizerEnabled(), config.atlasPhaseTimingsEnabled); }
	public boolean atlasMipParallelEnabled() {
		return supports(ATLAS_MIP_PARALLEL) && !quickPack.owns(ATLAS_MIP_PARALLEL)
			&& ((largeAtlasFixerEnabled() && config.atlasMipParallelEnabled) || startupAsyncFontAtlasEnabled());
	}
	public int atlasMipBatchSize() { return config.atlasMipBatchSize; }
	public boolean atlasDecodeBatchingEnabled() {
		return supports(ATLAS_DECODE_BATCHING)
			&& ((reloadOptimizerEnabled() && config.atlasDecodeBatchingEnabled) || startupAsyncFontAtlasEnabled());
	}
	public int atlasDecodeBatchSize() { return config.atlasDecodeBatchSize; }
	public boolean modelParseBatchingEnabled() {
		return supports(MODEL_PARSE_BATCHING)
			&& ((reloadOptimizerEnabled() && config.modelParseBatchingEnabled) || startupAsyncDataParsingEnabled());
	}
	public int modelParseBatchSize() { return config.modelParseBatchSize; }
	public boolean modelParseTimingEnabled() { return enabled(MODEL_PARSE_TIMINGS, reloadOptimizerEnabled(), config.modelParseTimingEnabled); }
	public boolean modelAdaptiveBatchingEnabled() {
		return supports(MODEL_ADAPTIVE_BATCHING)
			&& ((reloadOptimizerEnabled() && config.modelAdaptiveBatchingEnabled) || startupAsyncDataParsingEnabled());
	}
	public boolean modelDuplicateParseCacheEnabled() {
		return supports(MODEL_DUPLICATE_CACHE)
			&& ((reloadOptimizerEnabled() && config.modelDuplicateParseCacheEnabled) || startupAsyncDataParsingEnabled());
	}
	public boolean atlasCapEnabled() { return enabled(ATLAS_CAP, largeAtlasFixerEnabled(), config.atlasCapEnabled); }
	public int atlasCapPx() { return config.atlasCapPx; }
	public boolean atlasRetryEnabled() {
		return enabled(ATLAS_RETRY, largeAtlasFixerEnabled(), config.atlasRetryEnabled)
			&& !(config.forceDisablePartIIIWithIris && shaderPipelinePresent);
	}
	public boolean atlasRetryShaderGuardEnabled() {
		return enabled(ATLAS_RETRY, largeAtlasFixerEnabled(), config.forceDisablePartIIIWithIris);
	}
	public int atlasRetryMaxAttempts() { return config.atlasRetryMaxAttempts; }
	public boolean atlasExcludes(String atlasId) { return config.atlasExcludeIds != null && config.atlasExcludeIds.contains(atlasId); }
	public List<String> atlasExclusionIds() {
		return config.atlasExcludeIds == null ? List.of() : List.copyOf(config.atlasExcludeIds);
	}

	// These settings remain serialized for compatibility but are not delivered capabilities.
	public boolean experimentalAtlasSplitConfigured() { return false; }
	public boolean atlasSplitFallbackToDownscale() { return false; }
	public boolean atlasSplitDiagnostics() { return false; }

	public boolean startupOptimizerEnabled() { return enabled(STARTUP_OPTIMIZER, true, config.startupOptimizerEnabled); }
	public boolean startupTimingsEnabled() { return enabled(STARTUP_TIMINGS, startupOptimizerEnabled(), config.startupTimingsEnabled); }
	public boolean startupStatusOverlayEnabled() { return enabled(STARTUP_STATUS_OVERLAY, startupOptimizerEnabled(), config.startupStatusOverlayEnabled); }
	public boolean startupExecutorTuningEnabled() { return enabled(STARTUP_EXECUTOR_TUNING, startupOptimizerEnabled(), config.startupExecutorTuningEnabled); }
	public int startupWorkerThreads() { return config.startupWorkerThreads; }
	public int startupThreadPriority() { return config.startupThreadPriority; }
	public boolean startupSkipWithSmoothBoot() { return config.startupSkipWithSmoothBoot; }
	public boolean startupAsyncDataParsingEnabled() { return enabled(STARTUP_ASYNC_DATA, startupOptimizerEnabled(), config.startupAsyncDataParsingEnabled); }
	public boolean startupAsyncClassScanEnabled() { return enabled(STARTUP_ASYNC_CLASS_SCAN, startupOptimizerEnabled(), config.startupAsyncClassScanEnabled); }
	public boolean startupAsyncFontAtlasEnabled() { return enabled(STARTUP_ASYNC_FONT_ATLAS, startupOptimizerEnabled(), config.startupAsyncFontAtlasEnabled); }

	public boolean supports(PackForgeCapability capability) {
		return capabilities.supports(capability);
	}

	public Set<PackForgeCapability> availableCapabilities() {
		return capabilities.available();
	}

	public boolean quickPackLoaded() {
		return quickPack.loaded();
	}

	public boolean quickPackOwns(PackForgeCapability capability) {
		return quickPack.owns(capability);
	}

	public String quickPackDisabledReason(PackForgeCapability capability) {
		return quickPack.disabledReason(capability);
	}

	public QuickPackCompatibility.Profile quickPackProfile() {
		return quickPack;
	}

	private boolean enabled(PackForgeCapability capability, boolean parentGuard, boolean configured) {
		return supports(capability) && !quickPack.owns(capability) && parentGuard && configured;
	}
}
