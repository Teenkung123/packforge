package com.teenkung.packforge.config;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.OptionalModPresence;
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
import static com.teenkung.packforge.config.PackForgeCapability.RESOURCE_PACK_INDEX;

/** Public-API-only compatibility policy for the optional Quick Pack mod. */
public final class QuickPackCompatibility {
	public static final String MOD_ID = "quick-pack";

	private static final AtomicReference<Profile> CACHED = new AtomicReference<>();
	private static final Set<PackForgeCapability> KNOWN_OVERLAPS = Set.of(
		RESOURCE_PACK_INDEX,
		LOADING_FADE_CONTROL,
		FONT_PROVIDER_PRESELECTION,
		ATLAS_MIP_PARALLEL
	);
	private static final Pattern VERSION_PATTERN = Pattern.compile(
		"(?<!\\d)(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?!\\d)"
	);

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
			OptionalModPresence optionalMods = PackForgeServices.platform();
			if (!optionalMods.isModLoaded(MOD_ID)) {
				detected = Profile.absent();
			} else {
				detected = classify(optionalMods.modVersion(MOD_ID));
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
		String rawVersion = version == null ? "" : version.orElse("");
		return Profile.moduleHandoff(rawVersion, ownershipCapabilities(parseVersion(rawVersion)));
	}

	private static Optional<Version> parseVersion(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = VERSION_PATTERN.matcher(value);
		if (!matcher.find()) {
			return Optional.empty();
		}
		try {
			int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
			return Optional.of(new Version(
				Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)),
				patch
			));
		} catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	private static EnumSet<PackForgeCapability> ownershipCapabilities(Optional<Version> version) {
		if (version.isEmpty()) {
			return knownOverlaps();
		}
		Version parsed = version.get();
		if (parsed.major() > 1) {
			return knownOverlaps();
		}
		EnumSet<PackForgeCapability> capabilities = EnumSet.of(RESOURCE_PACK_INDEX);
		if (parsed.atLeast(1, 4)) {
			capabilities.add(LOADING_FADE_CONTROL);
		}
		if (parsed.atLeast(1, 5)) {
			capabilities.add(FONT_PROVIDER_PRESELECTION);
			capabilities.add(ATLAS_MIP_PARALLEL);
		}
		return capabilities;
	}

	private static EnumSet<PackForgeCapability> knownOverlaps() {
		return EnumSet.copyOf(KNOWN_OVERLAPS);
	}

	public enum Status {
		ABSENT,
		MODULE_HANDOFF,
		DETECTION_FAILED
	}

	public record Profile(Status status, Optional<String> version, Set<PackForgeCapability> ownedCapabilities) {
		public Profile {
			status = status == null ? Status.MODULE_HANDOFF : status;
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

		static Profile moduleHandoff(String version, Set<PackForgeCapability> capabilities) {
			return loaded(Status.MODULE_HANDOFF, version, capabilities);
		}

		static Profile detectionFailed() {
			return loaded(Status.DETECTION_FAILED, "", knownOverlaps());
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
				case MODULE_HANDOFF -> "Quick Pack owns this overlapping module";
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

		private static Profile loaded(Status status, String version, Set<PackForgeCapability> capabilities) {
			return new Profile(status, Optional.ofNullable(version), capabilities);
		}
	}

	private record Version(int major, int minor, int patch) implements Comparable<Version> {
		private boolean atLeast(int requiredMajor, int requiredMinor) {
			return compareTo(new Version(requiredMajor, requiredMinor, 0)) >= 0;
		}

		@Override
		public int compareTo(Version other) {
			int majorComparison = Integer.compare(major, other.major);
			if (majorComparison != 0) {
				return majorComparison;
			}
			int minorComparison = Integer.compare(minor, other.minor);
			return minorComparison != 0 ? minorComparison : Integer.compare(patch, other.patch);
		}
	}
}
