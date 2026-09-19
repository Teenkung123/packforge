package com.teenkung.packforge.platform;

import java.util.Objects;
import java.util.function.Predicate;

/** Immutable mod ownership facts, available before the platform entrypoint runs. */
public final class OptimizationCompatibility {
	public record State(boolean quickPackPresent, boolean rrlsPresent, boolean preservePlatformModelLoading,
			boolean shaderPipelinePresent) {
		public State(boolean quickPackPresent, boolean rrlsPresent, boolean preservePlatformModelLoading) {
			this(quickPackPresent, rrlsPresent, preservePlatformModelLoading, false);
		}
	}

	private static final State NONE = new State(false, false, false);
	private static volatile State earlyDiscovery;

	/** Captures only loader-owned discovery metadata; never loads another mod's classes. */
	public static State fromDiscovery(String loaderName, Predicate<String> isModLoaded) {
		Objects.requireNonNull(loaderName, "loaderName");
		Objects.requireNonNull(isModLoaded, "isModLoaded");
		return new State(
			isModLoaded.test("quick-pack") || isModLoaded.test("quick_pack"),
			isModLoaded.test("rrls"),
			PackForgeCompat.mustPreservePlatformModelLoading(loaderName, isModLoaded),
			isModLoaded.test("iris") || isModLoaded.test("oculus")
		);
	}

	/** Called by the mc26 loader adapter during mixin configuration initialization. */
	public static synchronized State installEarly(String loaderName, Predicate<String> isModLoaded) {
		State discovered = fromDiscovery(loaderName, isModLoaded);
		earlyDiscovery = discovered;
		return discovered;
	}

	public static State current() {
		State discovered = earlyDiscovery;
		if (discovered != null) return discovered;
		if (!PackForgeServices.isInitialized()) return NONE;
		return captureRuntimeDiscovery();
	}

	private static synchronized State captureRuntimeDiscovery() {
		if (earlyDiscovery == null) {
			PackForgePlatform platform = PackForgeServices.platform();
			earlyDiscovery = fromDiscovery(platform.loaderName(), platform::isModLoaded);
		}
		return earlyDiscovery;
	}

	/** Startup and reload plans keep this immutable value for their complete lifetime. */
	public static State capture() {
		return current();
	}

	private OptimizationCompatibility() {}
}
