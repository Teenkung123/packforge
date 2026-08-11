package com.teenkung.packforge.config;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgeServices;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_MIP_PARALLEL;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_PROVIDER_PRESELECTION;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_FADE_CONTROL;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_STATUS_OVERLAY;
import static com.teenkung.packforge.config.PackForgeCapability.RESOURCE_PACK_INDEX;
import static com.teenkung.packforge.config.PackForgeCapability.ZIP_READ_POOL;

/** Public-API-only compatibility policy for the optional Quick Pack mod. */
public final class QuickPackCompatibility {
	public static final String MOD_ID = "quick-pack";

	private static final Pattern VERSION_PREFIX = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
	private static final AtomicReference<Profile> CACHED = new AtomicReference<>();

	private QuickPackCompatibility() {}

	public static Profile current() {
		Profile cached = CACHED.get();
		if (cached != null) {
			return cached;
		}
		if (!PackForgeServices.isInitialized()) {
			return Profile.absent();
		}
		return detectAndLog();
	}

	/** Detects once during startup and logs one concise ownership summary. */
	public static Profile detectAndLog() {
		Profile cached = CACHED.get();
		if (cached != null) {
			return cached;
		}

		Profile detected;
		try {
			var platform = PackForgeServices.platform();
			if (!platform.isModLoaded(MOD_ID)) {
				detected = Profile.absent();
			} else {
				detected = classify(platform.modVersion(MOD_ID));
			}
		} catch (RuntimeException exception) {
			detected = Profile.detectionFailed();
			PackForge.LOGGER.warn("PackForge could not read Quick Pack metadata; applying conservative ownership", exception);
		}

		if (CACHED.compareAndSet(null, detected)) {
			if (detected.loaded()) {
				PackForge.LOGGER.info("PackForge Quick Pack compatibility: {}", detected.summary());
			}
			return detected;
		}
		return CACHED.get();
	}

	static Profile forTesting(String version) {
		return classify(Optional.ofNullable(version));
	}

	static Profile absentForTesting() {
		return Profile.absent();
	}

	private static Profile classify(Optional<String> version) {
		if (version.isEmpty() || version.get().isBlank()) {
			return Profile.unknownVersion("");
		}

		String rawVersion = version.get().trim();
		Matcher matcher = VERSION_PREFIX.matcher(rawVersion);
		if (!matcher.find()) {
			return Profile.unknownVersion(rawVersion);
		}

		try {
			int major = Integer.parseInt(matcher.group(1));
			int minor = matcher.group(2) == null ? -1 : Integer.parseInt(matcher.group(2));
			if (major != 1) {
				return Profile.unknownMajor(rawVersion);
			}
			if (minor != 5) {
				return Profile.unsupportedOneMajor(rawVersion);
			}
			return Profile.verifiedOneFive(rawVersion);
		} catch (NumberFormatException exception) {
			return Profile.unknownVersion(rawVersion);
		}
	}

	private static EnumSet<PackForgeCapability> ownershipCapabilities() {
		return EnumSet.of(
			RESOURCE_PACK_INDEX,
			ZIP_READ_POOL,
			FONT_PROVIDER_PRESELECTION,
			ATLAS_MIP_PARALLEL,
			LOADING_FADE_CONTROL,
			LOADING_STATUS_OVERLAY
		);
	}

	public enum Status {
		ABSENT,
		VERIFIED_1_5,
		UNSUPPORTED_1_X,
		UNKNOWN_VERSION,
		UNKNOWN_MAJOR,
		DETECTION_FAILED
	}

	public record Profile(Status status, Optional<String> version, Set<PackForgeCapability> ownedCapabilities) {
		public Profile {
			status = status == null ? Status.UNKNOWN_VERSION : status;
			version = version == null ? Optional.empty() : version.flatMap(value -> {
				String trimmed = value.trim();
				return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
			});
			ownedCapabilities = ownedCapabilities == null || ownedCapabilities.isEmpty()
				? Set.of()
				: Set.copyOf(ownedCapabilities);
		}

		static Profile absent() {
			return new Profile(Status.ABSENT, Optional.empty(), Set.of());
		}

		static Profile verifiedOneFive(String version) {
			return loaded(Status.VERIFIED_1_5, version);
		}

		static Profile unsupportedOneMajor(String version) {
			return loaded(Status.UNSUPPORTED_1_X, version);
		}

		static Profile unknownVersion(String version) {
			return loaded(Status.UNKNOWN_VERSION, version);
		}

		static Profile unknownMajor(String version) {
			return loaded(Status.UNKNOWN_MAJOR, version);
		}

		static Profile detectionFailed() {
			return loaded(Status.DETECTION_FAILED, "");
		}

		public boolean loaded() {
			return status != Status.ABSENT;
		}

		public boolean owns(PackForgeCapability capability) {
			return loaded() && ownedCapabilities.contains(capability);
		}

		public String owner(PackForgeCapability capability) {
			return owns(capability) ? MOD_ID : "";
		}

		public String disabledReason(PackForgeCapability capability) {
			if (!owns(capability)) {
				return "";
			}
			return switch (status) {
				case VERIFIED_1_5 -> "Quick Pack 1.5.x owns this path";
				case UNSUPPORTED_1_X -> "Quick Pack version is not explicitly verified; overlap is disabled conservatively";
				case UNKNOWN_VERSION -> "Quick Pack version is unavailable or unparsable; overlap is disabled conservatively";
				case UNKNOWN_MAJOR -> "Quick Pack major version is unknown; overlap is disabled conservatively";
				case DETECTION_FAILED -> "Quick Pack metadata detection failed; overlap is disabled conservatively";
				case ABSENT -> "";
			};
		}

		public String summary() {
			if (!loaded()) {
				return "absent";
			}
			String detectedVersion = version.map(value -> "version=" + value).orElse("version=unknown");
			return "status=" + status + " " + detectedVersion + " owns=" + ownedCapabilities;
		}

		private static Profile loaded(Status status, String version) {
			return new Profile(status, Optional.ofNullable(version), ownershipCapabilities());
		}
	}
}
