package com.teenkung.packforge.startup;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.platform.PackForgeCompat;
import com.teenkung.packforge.platform.PackForgeServices;

public final class StartupExecutorTuner {
	public static int tuneMaxThreads(String executorName, int vanillaThreads) {
		if (!startupExecutorTuningEnabled()) {
			StartupStatus.update("Skipping", "executor tuning disabled");
			return vanillaThreads;
		}
		if (skipForSmoothBoot()) {
			StartupStatus.update("Skipping", "executor tuning because Smooth Boot is installed");
			StartupTimings.executorTuning(executorName, vanillaThreads, vanillaThreads, StartupWorkerConfig.configuredThreadPriority(), "skipped_smoothboot");
			return vanillaThreads;
		}
		int configuredThreads = StartupWorkerConfig.configuredWorkerThreads();
		StartupStatus.update("Tuning", executorName + " executor to " + configuredThreads + " threads");
		StartupTimings.executorTuning(executorName, vanillaThreads, configuredThreads, StartupWorkerConfig.configuredThreadPriority(), "applied");
		return configuredThreads;
	}

	public static void applyThreadSettings(Thread thread, String name) {
		thread.setName(name);
		if (!startupExecutorTuningEnabled()) {
			return;
		}
		if (skipForSmoothBoot()) {
			return;
		}
		int priority = StartupWorkerConfig.configuredThreadPriority();
		if (thread.getPriority() != priority) {
			thread.setPriority(priority);
		}
	}

	private static boolean startupExecutorTuningEnabled() {
		if (PackForgeConfig.isLoaded()) {
			return FeatureFlags.startupExecutorTuningEnabled();
		}
		StartupEarlyConfig.Settings settings = StartupEarlyConfig.get();
		return settings.startupOptimizerEnabled() && settings.startupExecutorTuningEnabled();
	}

	private static boolean skipForSmoothBoot() {
		boolean skip = PackForgeConfig.isLoaded()
			? FeatureFlags.startupSkipWithSmoothBoot()
			: StartupEarlyConfig.get().startupSkipWithSmoothBoot();
		if (!skip) {
			return false;
		}
		if (PackForgeServices.isInitialized()) {
			return PackForgeCompat.isSmoothBootPresent();
		}
		return StartupEarlyConfig.isLikelySmoothBootPresent();
	}

	private StartupExecutorTuner() {}
}
