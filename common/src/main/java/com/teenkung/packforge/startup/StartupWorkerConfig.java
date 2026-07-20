package com.teenkung.packforge.startup;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.PackForgeConfig;

public final class StartupWorkerConfig {
	public static int configuredWorkerThreads() {
		int available = Runtime.getRuntime().availableProcessors();
		int configured = PackForgeConfig.isLoaded()
			? FeatureFlags.startupWorkerThreads()
			: StartupEarlyConfig.get().startupWorkerThreads();
		if (configured <= 0) {
			return autoWorkerThreads(available);
		}
		return clamp(configured, 1, available);
	}

	public static int configuredThreadPriority() {
		int configured = PackForgeConfig.isLoaded()
			? FeatureFlags.startupThreadPriority()
			: StartupEarlyConfig.get().startupThreadPriority();
		return clamp(configured, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
	}

	public static int autoWorkerThreads(int availableProcessors) {
		int available = Math.max(1, availableProcessors);
		return Math.max(1, Math.min(available / 2, available - 1));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private StartupWorkerConfig() {}
}
