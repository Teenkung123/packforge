package com.teenkung.packforge.config;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.OptionalModPresence;
import com.teenkung.packforge.platform.PackForgeServices;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_MIP_PARALLEL;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_PROVIDER_PRESELECTION;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_FADE_CONTROL;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_STATUS_OVERLAY;
import static com.teenkung.packforge.config.PackForgeCapability.RESOURCE_PACK_INDEX;
import static com.teenkung.packforge.config.PackForgeCapability.ZIP_READ_POOL;

/** Public-API-only compatibility policy for the optional Quick Pack mod. */
public final class QuickPackCompatibility {
	public static final String MOD_ID = "quick-pack";

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
		return Profile.moduleHandoff(version.orElse(""));
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

		static Profile moduleHandoff(String version) {
			return loaded(Status.MODULE_HANDOFF, version);
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

		private static Profile loaded(Status status, String version) {
			return new Profile(status, Optional.ofNullable(version), ownershipCapabilities());
		}
	}
}
