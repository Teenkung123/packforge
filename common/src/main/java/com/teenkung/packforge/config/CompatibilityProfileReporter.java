package com.teenkung.packforge.config;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.OptionalModPresence;
import com.teenkung.packforge.platform.PackForgeServices;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Emits loader-observed compatibility state only when the production harness requests it. */
public final class CompatibilityProfileReporter {
	static final String PROFILE_ID_ENV = "PACKFORGE_COMPAT_PROFILE_ID";
	static final String MOD_IDS_ENV = "PACKFORGE_COMPAT_MOD_IDS";
	private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final Set<PackForgeCapability> QUICK_PACK_OVERLAP = Set.copyOf(EnumSet.of(
		PackForgeCapability.RESOURCE_PACK_INDEX,
		PackForgeCapability.FONT_PROVIDER_PRESELECTION,
		PackForgeCapability.ATLAS_MIP_PARALLEL,
		PackForgeCapability.LOADING_FADE_CONTROL
	));

	public static void reportIfRequested() {
		Request request;
		try {
			request = requestFrom(System.getenv()).orElse(null);
		} catch (SecurityException exception) {
			// This reporter is optional. A production runtime that restricts environment
			// access must still start; a harness request will fail later on its missing marker.
			return;
		}
		if (request == null) {
			return;
		}

		var platform = PackForgeServices.platform();
		Snapshot snapshot = capture(
			request,
			platform,
			platform.loaderName(),
			PackForgeCapabilities.target(),
			FeaturePolicy.current()
		);
		PackForge.LOGGER.info("PackForge compatibility profile: {}", snapshot.summary());
	}

	static Optional<Request> requestFrom(Map<String, String> environment) {
		Objects.requireNonNull(environment, "environment");
		String profileId = trim(environment.get(PROFILE_ID_ENV));
		if (profileId.isEmpty()) {
			return Optional.empty();
		}
		if (!SAFE_ID.matcher(profileId).matches()) {
			throw new IllegalArgumentException("Invalid compatibility profile ID: " + profileId);
		}

		TreeSet<String> modIds = new TreeSet<>();
		for (String token : trim(environment.get(MOD_IDS_ENV)).split(",")) {
			String modId = token.trim();
			if (modId.isEmpty()) {
				continue;
			}
			if (!SAFE_ID.matcher(modId).matches()) {
				throw new IllegalArgumentException("Invalid compatibility profile mod ID: " + modId);
			}
			if (!modIds.add(modId)) {
				throw new IllegalArgumentException("Duplicate compatibility profile mod ID: " + modId);
			}
		}
		if (modIds.isEmpty()) {
			throw new IllegalArgumentException("Compatibility profile must declare at least one mod ID");
		}
		return Optional.of(new Request(profileId, List.copyOf(modIds)));
	}

	static Snapshot capture(
		Request request,
		OptionalModPresence optionalMods,
		String loader,
		String target,
		FeaturePolicy policy
	) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(optionalMods, "optionalMods");
		Objects.requireNonNull(policy, "policy");
		List<ModStatus> mods = new ArrayList<>();
		for (String modId : request.modIds()) {
			boolean loaded = optionalMods.isModLoaded(modId);
			String version = loaded ? optionalMods.modVersion(modId).orElse("unknown") : "absent";
			mods.add(new ModStatus(modId, loaded, normalizeVersion(version)));
		}

		Map<PackForgeCapability, Boolean> effective = effectiveCapabilities(policy);
		Map<String, Boolean> overlap = new TreeMap<>();
		Map<String, Boolean> retained = new TreeMap<>();
		for (PackForgeCapability capability : PackForgeCapability.values()) {
			(QUICK_PACK_OVERLAP.contains(capability) ? overlap : retained)
				.put(capability.name(), effective.get(capability));
		}

		return new Snapshot(
			request.profileId(),
			trim(loader),
			trim(target),
			List.copyOf(mods),
			policy.quickPackProfile().status(),
			policy.quickPackProfile().ownedCapabilities().stream().map(Enum::name).sorted().toList(),
			Map.copyOf(overlap),
			Map.copyOf(retained)
		);
	}

	private static Map<PackForgeCapability, Boolean> effectiveCapabilities(FeaturePolicy policy) {
		EnumMap<PackForgeCapability, Boolean> effective = new EnumMap<>(PackForgeCapability.class);
		for (PackForgeCapability capability : PackForgeCapability.values()) {
			effective.put(capability, switch (capability) {
				case RESOURCE_PACK_INDEX -> policy.loaderIndexEnabled();
				case ZIP_READ_POOL -> policy.loaderZipPoolEnabled();
				case LOADER_TIMINGS -> policy.loaderTimingsEnabled();
				case RELOAD_LISTENER_TIMINGS -> policy.reloadListenerTimingsEnabled();
				case SHADER_STALL_DIAGNOSTICS -> policy.shaderApplyStallDiagnosticsEnabled();
				case IMMEDIATELY_FAST_FONT_ATLAS_COMPAT -> policy.immediatelyFastFontAtlasCompatEnabled();
				case LOADING_STATUS_OVERLAY -> policy.loadingStatusOverlayEnabled();
				case LOADING_FADE_CONTROL -> policy.loadingScreenFadeOutDisabled();
				case RELOAD_SUMMARY_TOAST -> policy.reloadSummaryToastEnabled();
				case MODEL_UV_TRANSPARENCY_CLAMP -> policy.modelUvTransparencyClampEnabled();
				case FONT_RELOAD_DIAGNOSTICS -> policy.fontReloadDiagnosticsEnabled();
				case FONT_PROVIDER_PRESELECTION -> policy.fontPrepareProviderSelectionEnabled();
				case FONT_BITMAP_CACHE -> policy.fontBitmapProviderCacheEnabled();
				case ATLAS_PHASE_TIMINGS -> policy.atlasPhaseTimingsEnabled();
				case ATLAS_MIP_PARALLEL -> policy.atlasMipParallelEnabled();
				case ATLAS_DECODE_BATCHING -> policy.atlasDecodeBatchingEnabled();
				case MODEL_PARSE_BATCHING -> policy.modelParseBatchingEnabled();
				case MODEL_PARSE_TIMINGS -> policy.modelParseTimingEnabled();
				case MODEL_ADAPTIVE_BATCHING -> policy.modelAdaptiveBatchingEnabled();
				case MODEL_DUPLICATE_CACHE -> policy.modelDuplicateParseCacheEnabled();
				case ATLAS_CAP -> policy.atlasCapEnabled();
				case ATLAS_RETRY -> policy.atlasRetryEnabled();
				case STARTUP_OPTIMIZER -> policy.startupOptimizerEnabled();
				case STARTUP_TIMINGS -> policy.startupTimingsEnabled();
				case STARTUP_STATUS_OVERLAY -> policy.startupStatusOverlayEnabled();
				case STARTUP_EXECUTOR_TUNING -> policy.startupExecutorTuningEnabled();
				case STARTUP_ASYNC_DATA -> policy.startupAsyncDataParsingEnabled();
				case STARTUP_ASYNC_CLASS_SCAN -> policy.startupAsyncClassScanEnabled();
				case STARTUP_ASYNC_FONT_ATLAS -> policy.startupAsyncFontAtlasEnabled();
			});
		}
		return effective;
	}

	private static String mapSummary(Map<String, Boolean> values) {
		return values.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> entry.getKey() + ":" + entry.getValue())
			.collect(Collectors.joining(","));
	}

	private static String normalizeVersion(String value) {
		String trimmed = trim(value);
		if (trimmed.isEmpty()) {
			return "unknown";
		}
		StringBuilder result = new StringBuilder(Math.min(trimmed.length(), 128));
		for (int index = 0; index < trimmed.length() && result.length() < 128; index++) {
			char character = trimmed.charAt(index);
			result.append((character >= 'a' && character <= 'z')
				|| (character >= 'A' && character <= 'Z')
				|| (character >= '0' && character <= '9')
				|| "._+~-".indexOf(character) >= 0 ? character : '_');
		}
		return result.toString();
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	record Request(String profileId, List<String> modIds) {
		Request {
			profileId = Objects.requireNonNull(profileId, "profileId");
			modIds = List.copyOf(Objects.requireNonNull(modIds, "modIds"));
		}
	}

	record ModStatus(String id, boolean loaded, String version) {
		String summary() {
			return id + ":" + loaded + ":" + version;
		}
	}

	record Snapshot(
		String profileId,
		String loader,
		String target,
		List<ModStatus> mods,
		QuickPackCompatibility.Status quickPackStatus,
		List<String> quickPackOwnedCapabilities,
		Map<String, Boolean> overlap,
		Map<String, Boolean> retained
	) {
		String summary() {
			return "id=" + profileId
				+ " loader=" + loader
				+ " target=" + target
				+ " mods=" + mods.stream().map(ModStatus::summary).collect(Collectors.joining(","))
				+ " quickPackStatus=" + quickPackStatus
				+ " quickPackOwns=" + String.join(",", quickPackOwnedCapabilities)
				+ " overlap=" + mapSummary(overlap)
				+ " retained=" + mapSummary(retained);
		}
	}

	private CompatibilityProfileReporter() {}
}
