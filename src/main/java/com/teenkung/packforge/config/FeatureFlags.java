package com.teenkung.packforge.config;

public final class FeatureFlags {
	public static boolean loaderIndexEnabled() { return PackForgeConfig.get().loaderIndexEnabled; }
	public static boolean loaderTimingsEnabled() { return PackForgeConfig.get().loaderTimingsEnabled; }
	public static boolean reloadListenerTimingsEnabled() { return PackForgeConfig.get().reloadListenerTimingsEnabled; }
	public static boolean loadingStatusOverlayEnabled() { return PackForgeConfig.get().loadingStatusOverlayEnabled; }
	public static boolean modelUvTransparencyClampEnabled() { return PackForgeConfig.get().modelUvTransparencyClampEnabled; }
	public static boolean fontReloadDiagnosticsEnabled() { return PackForgeConfig.get().fontReloadDiagnosticsEnabled; }
	public static boolean fontPrepareProviderSelectionEnabled() { return PackForgeConfig.get().fontPrepareProviderSelectionEnabled; }
	public static boolean fontBitmapProviderCacheEnabled() { return PackForgeConfig.get().fontBitmapProviderCacheEnabled; }
	public static boolean atlasCapEnabled() { return PackForgeConfig.get().atlasCapEnabled; }
	public static int atlasCapPx() { return PackForgeConfig.get().atlasCapPx; }
	public static boolean atlasRetryEnabled() { return PackForgeConfig.get().atlasRetryEnabled; }
	public static int atlasRetryMaxAttempts() { return PackForgeConfig.get().atlasRetryMaxAttempts; }
	public static boolean atlasExcludes(String atlasId) { return PackForgeConfig.get().atlasExcludeIds.contains(atlasId); }

	private FeatureFlags() {}
}
