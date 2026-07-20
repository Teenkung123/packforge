package com.teenkung.packforge.config;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Immutable target capability profile parsed from the generated resource. */
public final class PackForgeCapabilityProfile {
	private final String target;
	private final Set<PackForgeCapability> available;
	private final Set<String> unknownIdentifiers;
	private final Properties properties;

	private PackForgeCapabilityProfile(
		String target,
		Set<PackForgeCapability> available,
		Set<String> unknownIdentifiers,
		Properties properties
	) {
		this.target = target;
		this.available = immutableCapabilities(available);
		this.unknownIdentifiers = Collections.unmodifiableSet(new LinkedHashSet<>(unknownIdentifiers));
		this.properties = copy(properties);
	}

	public static PackForgeCapabilityProfile currentDevelopment() {
		return new PackForgeCapabilityProfile(
			"development-current",
			EnumSet.allOf(PackForgeCapability.class),
			Set.of(),
			new Properties()
		);
	}

	public static PackForgeCapabilityProfile safeFallback() {
		return new PackForgeCapabilityProfile(
			"missing-capability-profile",
			EnumSet.noneOf(PackForgeCapability.class),
			Set.of(),
			new Properties()
		);
	}

	public static PackForgeCapabilityProfile fromProperties(Properties properties) {
		Objects.requireNonNull(properties, "properties");
		String target = properties.getProperty("target", "unknown-target").trim();
		if (target.isEmpty()) {
			target = "unknown-target";
		}

		EnumSet<PackForgeCapability> available = EnumSet.noneOf(PackForgeCapability.class);
		LinkedHashSet<String> unknown = new LinkedHashSet<>();
		String configured = properties.getProperty("capabilities", "");
		for (String token : configured.split(",")) {
			String identifier = token.trim();
			if (identifier.isEmpty()) {
				continue;
			}
			try {
				available.add(PackForgeCapability.valueOf(identifier));
			} catch (IllegalArgumentException ignored) {
				unknown.add(identifier);
			}
		}
		return new PackForgeCapabilityProfile(target, available, unknown, properties);
	}

	public String target() {
		return target;
	}

	public boolean supports(PackForgeCapability capability) {
		return available.contains(Objects.requireNonNull(capability, "capability"));
	}

	public Set<PackForgeCapability> available() {
		return available;
	}

	public Set<PackForgeCapability> unavailable() {
		EnumSet<PackForgeCapability> unavailable = EnumSet.allOf(PackForgeCapability.class);
		unavailable.removeAll(available);
		return Collections.unmodifiableSet(unavailable);
	}

	public Set<String> unknownIdentifiers() {
		return unknownIdentifiers;
	}

	public Optional<String> unavailableReason(PackForgeCapability capability) {
		Objects.requireNonNull(capability, "capability");
		if (supports(capability)) {
			return Optional.empty();
		}
		String configured = properties.getProperty("reason." + capability.name());
		if (configured == null || configured.isBlank()) {
			configured = properties.getProperty("unavailable." + capability.name());
		}
		if (configured == null || configured.isBlank()) {
			configured = "Not available for Minecraft target " + target;
		}
		return Optional.of(configured.trim());
	}

	private static Set<PackForgeCapability> immutableCapabilities(Set<PackForgeCapability> values) {
		EnumSet<PackForgeCapability> copy = values.isEmpty()
			? EnumSet.noneOf(PackForgeCapability.class)
			: EnumSet.copyOf(values);
		return Collections.unmodifiableSet(copy);
	}

	private static Properties copy(Properties source) {
		Properties copy = new Properties();
		copy.putAll(source);
		return copy;
	}
}
