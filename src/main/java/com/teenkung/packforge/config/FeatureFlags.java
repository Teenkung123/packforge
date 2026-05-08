package com.teenkung.packforge.config;

public final class FeatureFlags {
	public static boolean reloadOptimizerEnabled() { return PackForgeConfig.get().reloadOptimizerEnabled; }
	public static boolean largeAtlasFixerEnabled() { return PackForgeConfig.get().largeAtlasFixerEnabled; }
	public static boolean loaderIndexEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loaderIndexEnabled; }
	public static boolean loaderZipPoolEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loaderZipPoolEnabled; }
	public static boolean loaderTimingsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loaderTimingsEnabled; }
	public static boolean reloadListenerTimingsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().reloadListenerTimingsEnabled; }
	public static boolean loadingStatusOverlayEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().loadingStatusOverlayEnabled; }
	public static boolean modelUvTransparencyClampEnabled() { return largeAtlasFixerEnabled() && PackForgeConfig.get().modelUvTransparencyClampEnabled; }
	public static boolean fontReloadDiagnosticsEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().fontReloadDiagnosticsEnabled; }
	public static boolean fontPrepareProviderSelectionEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().fontPrepareProviderSelectionEnabled; }
	public static boolean fontBitmapProviderCacheEnabled() { return reloadOptimizerEnabled() && PackForgeConfig.get().fontBitmapProviderCacheEnabled; }
	public static boolean atlasCapEnabled() { return largeAtlasFixerEnabled() && PackForgeConfig.get().atlasCapEnabled; }
	public static int atlasCapPx() { return PackForgeConfig.get().atlasCapPx; }
	public static boolean atlasRetryEnabled() { return largeAtlasFixerEnabled() && PackForgeConfig.get().atlasRetryEnabled; }
	public static int atlasRetryMaxAttempts() { return PackForgeConfig.get().atlasRetryMaxAttempts; }
	public static boolean atlasExcludes(String atlasId) { return PackForgeConfig.get().atlasExcludeIds.contains(atlasId); }

	private FeatureFlags() {}
}
