package com.teenkung.packforge.config;

public final class FeatureFlags {
	public static boolean loaderIndexEnabled() { return PackForgeConfig.get().loaderIndexEnabled; }
	public static boolean loaderTimingsEnabled() { return PackForgeConfig.get().loaderTimingsEnabled; }
	public static boolean atlasCapEnabled() { return PackForgeConfig.get().atlasCapEnabled; }
	public static int atlasCapPx() { return PackForgeConfig.get().atlasCapPx; }
	public static boolean atlasRetryEnabled() { return PackForgeConfig.get().atlasRetryEnabled; }
	public static int atlasRetryMaxAttempts() { return PackForgeConfig.get().atlasRetryMaxAttempts; }
	public static boolean atlasExcludes(String atlasId) { return PackForgeConfig.get().atlasExcludeIds.contains(atlasId); }

	private FeatureFlags() {}
}
