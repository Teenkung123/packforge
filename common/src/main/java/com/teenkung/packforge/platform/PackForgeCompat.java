package com.teenkung.packforge.platform;

public final class PackForgeCompat {
	public static boolean isSodiumLikePresent() {
		PackForgePlatform platform = PackForgeServices.platform();
		return platform.isModLoaded("sodium") || platform.isModLoaded("embeddium");
	}

	public static boolean isShaderPipelinePresent() {
		PackForgePlatform platform = PackForgeServices.platform();
		return platform.isModLoaded("iris") || platform.isModLoaded("oculus");
	}

	public static boolean isSmoothBootPresent() {
		PackForgePlatform platform = PackForgeServices.platform();
		return platform.isModLoaded("smoothboot")
			|| platform.isModLoaded("smoothboot-fabric")
			|| platform.isModLoaded("smoothboot-reloaded");
	}

	public static boolean isImmediatelyFastPresent() {
		return PackForgeServices.platform().isModLoaded("immediatelyfast");
	}

	private PackForgeCompat() {}
}
