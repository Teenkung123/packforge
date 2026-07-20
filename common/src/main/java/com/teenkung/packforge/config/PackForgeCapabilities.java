package com.teenkung.packforge.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Loads the exact target's build-generated capability profile. */
public final class PackForgeCapabilities {
	public static final String RESOURCE_NAME = "packforge-capabilities.properties";
	private static final PackForgeCapabilityProfile PROFILE = loadFromClasspath();

	public static boolean supports(PackForgeCapability capability) {
		return PROFILE.supports(capability);
	}

	public static String target() {
		return PROFILE.target();
	}

	public static Set<PackForgeCapability> available() {
		return PROFILE.available();
	}

	public static Set<PackForgeCapability> unavailable() {
		return PROFILE.unavailable();
	}

	public static Set<String> unknownIdentifiers() {
		return PROFILE.unknownIdentifiers();
	}

	public static String unavailableReason(PackForgeCapability capability) {
		return PROFILE.unavailableReason(capability).orElse("");
	}

	static PackForgeCapabilityProfile load(InputStream input) throws IOException {
		Objects.requireNonNull(input, "input");
		Properties properties = new Properties();
		properties.load(input);
		return PackForgeCapabilityProfile.fromProperties(properties);
	}

	private static PackForgeCapabilityProfile loadFromClasspath() {
		ClassLoader loader = PackForgeCapabilities.class.getClassLoader();
		try (InputStream input = loader.getResourceAsStream(RESOURCE_NAME)) {
			if (input == null) {
				// A damaged artifact must preserve vanilla behavior instead of enabling
				// target-specific paths without an authoritative capability profile.
				return PackForgeCapabilityProfile.safeFallback();
			}
			return load(input);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to read " + RESOURCE_NAME, exception);
		}
	}

	private PackForgeCapabilities() {}
}
