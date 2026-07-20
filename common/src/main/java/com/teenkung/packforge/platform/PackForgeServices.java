package com.teenkung.packforge.platform;

import java.util.Objects;

public final class PackForgeServices {
	private static volatile PackForgePlatform platform;

	public static void init(PackForgePlatform value) {
		platform = Objects.requireNonNull(value, "platform");
	}

	public static boolean isInitialized() {
		return platform != null;
	}

	public static PackForgePlatform platform() {
		PackForgePlatform value = platform;
		if (value == null) {
			throw new IllegalStateException("PackForge platform not initialized");
		}
		return value;
	}

	private PackForgeServices() {}
}
