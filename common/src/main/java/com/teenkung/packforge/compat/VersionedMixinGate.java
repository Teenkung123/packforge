package com.teenkung.packforge.compat;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Applies build-generated per-module Minecraft floors before target classes are transformed. */
public final class VersionedMixinGate {
	private static final String RESOURCE_NAME = "packforge-capabilities.properties";
	private static final String MIXIN_FLOOR_PREFIX = "mixinFloor.";
	private static final Profile PROFILE = loadProfile();

	public static boolean shouldApply(String mixinClassName, String runtimeMinecraftVersion) {
		return PROFILE.shouldApply(mixinClassName, runtimeMinecraftVersion);
	}

	static boolean shouldApply(String mixinClassName, String runtimeMinecraftVersion, Properties properties) {
		return Profile.from(properties).shouldApply(mixinClassName, runtimeMinecraftVersion);
	}

	private static Profile loadProfile() {
		ClassLoader loader = VersionedMixinGate.class.getClassLoader();
		try (InputStream input = loader.getResourceAsStream(RESOURCE_NAME)) {
			if (input == null) {
				return Profile.invalid();
			}
			Properties properties = new Properties();
			properties.load(input);
			return Profile.from(properties);
		} catch (IOException exception) {
			return Profile.invalid();
		}
	}

	private record Profile(boolean valid, Map<String, String> minimumVersions) {
		private static Profile invalid() {
			return new Profile(false, Map.of());
		}

		private static Profile from(Properties properties) {
			Map<String, String> floors = new LinkedHashMap<>();
			for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
				if (key.startsWith(MIXIN_FLOOR_PREFIX)) {
					String mixin = key.substring(MIXIN_FLOOR_PREFIX.length()).trim();
					String minimum = properties.getProperty(key, "").trim();
					if (!mixin.isEmpty() && !minimum.isEmpty()) {
						floors.put(mixin, minimum);
					}
				}
			}
			return new Profile(true, Map.copyOf(floors));
		}

		private boolean shouldApply(String mixinClassName, String runtimeMinecraftVersion) {
			if (!valid || mixinClassName == null) {
				return false;
			}
			for (Map.Entry<String, String> entry : minimumVersions.entrySet()) {
				if (mixinClassName.endsWith(entry.getKey())) {
					return MinecraftVersion.isAtLeast(runtimeMinecraftVersion, entry.getValue());
				}
			}
			return true;
		}
	}

	private VersionedMixinGate() {}
}
