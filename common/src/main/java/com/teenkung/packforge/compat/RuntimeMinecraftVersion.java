package com.teenkung.packforge.compat;

/** Process-wide Minecraft version discovered from the active loader. */
public final class RuntimeMinecraftVersion {
	private static volatile String current = "";

	public static void configure(String minecraftVersion) {
		current = minecraftVersion == null ? "" : minecraftVersion.trim();
	}

	public static String current() {
		return current;
	}

	public static boolean isAtLeast(String minimumVersion) {
		return MinecraftVersion.isAtLeast(current, minimumVersion);
	}

	private RuntimeMinecraftVersion() {}
}
