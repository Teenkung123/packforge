package com.teenkung.packforge.platform;

import java.util.function.Predicate;

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

	public static boolean mustPreservePlatformModelLoading() {
		PackForgePlatform platform = PackForgeServices.platform();
		return mustPreservePlatformModelLoading(platform.loaderName(), platform::isModLoaded);
	}

	static boolean mustPreservePlatformModelLoading(String loaderName, Predicate<String> isModLoaded) {
		return "fabric".equals(loaderName) && isModLoaded.test("fabric-model-loading-api-v1");
	}

	private PackForgeCompat() {}
}
