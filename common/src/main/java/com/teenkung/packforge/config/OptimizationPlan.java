package com.teenkung.packforge.config;

import com.teenkung.packforge.platform.OptimizationCompatibility;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Internal, immutable ownership and admission decisions for one configuration boundary. */
public final class OptimizationPlan {
	public enum Owner { PACKFORGE, MINECRAFT, QUICK_PACK, RRLS, PLATFORM }

	public record Decision(boolean requested, boolean effective, Owner owner, String reason) {
		public Decision {
			Objects.requireNonNull(owner, "owner");
			Objects.requireNonNull(reason, "reason");
		}
	}

	private final Map<PackForgeCapability, Decision> decisions;

	private OptimizationPlan(Map<PackForgeCapability, Decision> decisions) {
		this.decisions = Collections.unmodifiableMap(new EnumMap<>(decisions));
	}

	public static OptimizationPlan capture(PackForgeConfig.Cfg config) {
		return create(config, PackForgeCapabilities.available(), PackForgeCapabilities.target(),
			OptimizationCompatibility.current());
	}

	public static OptimizationPlan create(PackForgeConfig.Cfg config, Set<PackForgeCapability> capabilities,
			String target, OptimizationCompatibility.State compatibility) {
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(capabilities, "capabilities");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(compatibility, "compatibility");
		Map<PackForgeCapability, Decision> decisions = new EnumMap<>(PackForgeCapability.class);
		for (PackForgeCapability capability : PackForgeCapability.values()) {
			boolean requested = requested(config, capability);
			if (capability == PackForgeCapability.ATLAS_CAP) {
				decisions.put(capability, new Decision(
					requested,
					false,
					Owner.MINECRAFT,
					requested
						? "Original texture dimensions are preserved; atlas sprite cap is not admitted"
						: "Original texture dimensions are preserved"
				));
				continue;
			}
			Owner owner = externalOwner(capability, compatibility);
			String reason;
			boolean effective = false;
			if (owner != Owner.PACKFORGE) {
				reason = switch (owner) {
					case QUICK_PACK -> "Quick Pack owns this stage; PackForge hooks are disabled";
					case RRLS -> "RRLS owns the reload overlay";
					case PLATFORM -> "Preserving the platform model-loading API and custom loaders";
					default -> throw new IllegalStateException("Unexpected external owner " + owner);
				};
			} else if (withdrawn(capability, target)) {
				owner = Owner.MINECRAFT;
				reason = target.startsWith("mc26")
					? "Bitmap retention withdrawn on mc26: memory cost starved font preparation"
					: "Bitmap retention is not admitted with coordinated font preparation";
			} else if (!capabilities.contains(capability)) {
				owner = Owner.MINECRAFT;
				reason = "Not supported by this Minecraft/loader target";
			} else if (shaderGuard(config, capability, compatibility)) {
				owner = Owner.MINECRAFT;
				reason = "Atlas retry disabled by the saved Iris/Oculus compatibility guard";
			} else if (!configured(config, capability, capabilities)) {
				owner = Owner.MINECRAFT;
				reason = requested ? "Disabled by its parent setting" : "Disabled in configuration";
			} else {
				effective = true;
				reason = "Enabled";
			}
			decisions.put(capability, new Decision(requested, effective, owner, reason));
		}
		return new OptimizationPlan(decisions);
	}

	public Decision decision(PackForgeCapability capability) { return decisions.get(capability); }
	public boolean enabled(PackForgeCapability capability) { return decision(capability).effective(); }
	public Map<PackForgeCapability, Decision> decisions() { return decisions; }

	/** Allocation-free lookup for bootstrap callers that do not yet own a reload snapshot. */
	static boolean enabledNow(PackForgeCapability capability) {
		if (capability == PackForgeCapability.ATLAS_CAP) {
			return false;
		}
		OptimizationCompatibility.State compatibility = OptimizationCompatibility.current();
		PackForgeConfig.Cfg config = PackForgeConfig.get();
		return externalOwner(capability, compatibility) == Owner.PACKFORGE
			&& !shaderGuard(config, capability, compatibility)
			&& !withdrawn(capability, PackForgeCapabilities.target())
			&& PackForgeCapabilities.supports(capability)
			&& configured(config, capability, PackForgeCapabilities.available());
	}

	private static boolean shaderGuard(PackForgeConfig.Cfg config, PackForgeCapability capability,
			OptimizationCompatibility.State compatibility) {
		return capability == PackForgeCapability.ATLAS_RETRY && config.forceDisablePartIIIWithIris
			&& compatibility.shaderPipelinePresent();
	}

	private static boolean withdrawn(PackForgeCapability capability, String target) {
		return (target.startsWith("mc26") || target.equals("mc1_20_1") || target.equals("mc1_21_11")
			|| target.equals("mc1_21_1") || target.equals("mc1_21_4") || target.equals("mc1_21_8"))
			&& capability == PackForgeCapability.FONT_BITMAP_CACHE;
	}

	private static Owner externalOwner(PackForgeCapability capability, OptimizationCompatibility.State compatibility) {
		if (compatibility.rrlsPresent() && (capability == PackForgeCapability.LOADING_STATUS_OVERLAY
				|| capability == PackForgeCapability.LOADING_FADE_CONTROL
				|| capability == PackForgeCapability.STARTUP_STATUS_OVERLAY)) return Owner.RRLS;
		if (compatibility.quickPackPresent()) {
			switch (capability) {
				case FONT_PROVIDER_PRESELECTION, FONT_BITMAP_CACHE, FONT_RELOAD_DIAGNOSTICS,
					ATLAS_MIP_PARALLEL, ATLAS_DECODE_BATCHING, ATLAS_PHASE_TIMINGS, ATLAS_RETRY,
					RESOURCE_PACK_INDEX, ZIP_READ_POOL, RESOURCE_READ_REUSE,
					MODEL_PARSE_BATCHING, MODEL_ADAPTIVE_BATCHING, MODEL_DUPLICATE_CACHE, MODEL_PARSE_TIMINGS,
					STARTUP_EXECUTOR_TUNING, STARTUP_ASYNC_DATA, STARTUP_ASYNC_FONT_ATLAS,
					LOADING_STATUS_OVERLAY, LOADING_FADE_CONTROL, STARTUP_STATUS_OVERLAY -> { return Owner.QUICK_PACK; }
				default -> { }
			}
		}
		if (compatibility.preservePlatformModelLoading()) {
			switch (capability) {
				case MODEL_PARSE_BATCHING, MODEL_ADAPTIVE_BATCHING, MODEL_DUPLICATE_CACHE, STARTUP_ASYNC_DATA -> { return Owner.PLATFORM; }
				default -> { }
			}
		}
		return Owner.PACKFORGE;
	}

	private static boolean configured(PackForgeConfig.Cfg config, PackForgeCapability capability,
			Set<PackForgeCapability> capabilities) {
		boolean startup = config.startupOptimizerEnabled && capabilities.contains(PackForgeCapability.STARTUP_OPTIMIZER);
		boolean fontShortcut = startup && config.startupAsyncFontAtlasEnabled
			&& capabilities.contains(PackForgeCapability.STARTUP_ASYNC_FONT_ATLAS);
		boolean modelShortcut = startup && config.startupAsyncDataParsingEnabled
			&& capabilities.contains(PackForgeCapability.STARTUP_ASYNC_DATA);
		return switch (capability) {
			case LOADER_TIMINGS -> config.loaderTimingsEnabled;
			case STARTUP_OPTIMIZER -> startup;
			case STARTUP_TIMINGS, STARTUP_STATUS_OVERLAY, STARTUP_EXECUTOR_TUNING,
				STARTUP_ASYNC_DATA, STARTUP_ASYNC_CLASS_SCAN, STARTUP_ASYNC_FONT_ATLAS -> startup && requested(config, capability);
			case MODEL_UV_TRANSPARENCY_CLAMP, ATLAS_RETRY -> config.largeAtlasFixerEnabled && requested(config, capability);
			case ATLAS_MIP_PARALLEL -> config.cpuMipPreparationEnabled || fontShortcut;
			case FONT_PROVIDER_PRESELECTION, ATLAS_DECODE_BATCHING -> (config.reloadOptimizerEnabled && requested(config, capability)) || fontShortcut;
			case MODEL_PARSE_BATCHING, MODEL_ADAPTIVE_BATCHING, MODEL_DUPLICATE_CACHE -> (config.reloadOptimizerEnabled && requested(config, capability)) || modelShortcut;
			default -> config.reloadOptimizerEnabled && requested(config, capability);
		};
	}

	private static boolean requested(PackForgeConfig.Cfg config, PackForgeCapability capability) {
		return switch (capability) {
			case RESOURCE_PACK_INDEX -> config.loaderIndexEnabled;
			case RESOURCE_READ_REUSE -> config.resourceReadReuseEnabled;
			case ZIP_READ_POOL -> config.loaderZipPoolEnabled;
			case LOADER_TIMINGS -> config.loaderTimingsEnabled;
			case RELOAD_LISTENER_TIMINGS -> config.reloadListenerTimingsEnabled;
			case SHADER_STALL_DIAGNOSTICS -> config.shaderApplyStallDiagnosticsEnabled;
			case IMMEDIATELY_FAST_FONT_ATLAS_COMPAT -> config.immediatelyFastFontAtlasCompatEnabled;
			case LOADING_STATUS_OVERLAY -> config.loadingStatusOverlayEnabled;
			case LOADING_FADE_CONTROL -> config.loadingScreenFadeOutDisabled;
			case RELOAD_SUMMARY_TOAST -> config.reloadSummaryToastEnabled;
			case MODEL_UV_TRANSPARENCY_CLAMP -> config.modelUvTransparencyClampEnabled;
			case FONT_RELOAD_DIAGNOSTICS -> config.fontReloadDiagnosticsEnabled;
			case FONT_PROVIDER_PRESELECTION -> config.fontPrepareProviderSelectionEnabled;
			case FONT_BITMAP_CACHE -> config.fontBitmapProviderCacheEnabled;
			case ATLAS_PHASE_TIMINGS -> config.atlasPhaseTimingsEnabled;
			case ATLAS_MIP_PARALLEL -> config.cpuMipPreparationEnabled;
			case ATLAS_DECODE_BATCHING -> config.atlasDecodeBatchingEnabled;
			case MODEL_PARSE_BATCHING -> config.modelParseBatchingEnabled;
			case MODEL_PARSE_TIMINGS -> config.modelParseTimingEnabled;
			case MODEL_ADAPTIVE_BATCHING -> config.modelAdaptiveBatchingEnabled;
			case MODEL_DUPLICATE_CACHE -> config.modelDuplicateParseCacheEnabled;
			case ATLAS_CAP -> config.atlasCapEnabled;
			case ATLAS_RETRY -> config.atlasRetryEnabled;
			case STARTUP_OPTIMIZER -> config.startupOptimizerEnabled;
			case STARTUP_TIMINGS -> config.startupTimingsEnabled;
			case STARTUP_STATUS_OVERLAY -> config.startupStatusOverlayEnabled;
			case STARTUP_EXECUTOR_TUNING -> config.startupExecutorTuningEnabled;
			case STARTUP_ASYNC_DATA -> config.startupAsyncDataParsingEnabled;
			case STARTUP_ASYNC_CLASS_SCAN -> config.startupAsyncClassScanEnabled;
			case STARTUP_ASYNC_FONT_ATLAS -> config.startupAsyncFontAtlasEnabled;
		};
	}
}
