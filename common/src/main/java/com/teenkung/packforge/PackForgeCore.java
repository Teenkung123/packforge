package com.teenkung.packforge;

import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.platform.PackForgeServices;
import com.teenkung.packforge.startup.StartupAsyncFeatures;
import com.teenkung.packforge.startup.StartupExecutorTuner;
import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;

public final class PackForgeCore {
	public static void init() {
		if (StartupTimings.hasRecordedWork()) {
			StartupTimings.markBoundary("packforge_core_start", "bootstrap_before_packforge");
		} else {
			StartupTimings.resetIfUnused();
		}
		StartupStatus.start();
		StartupStatus.update("Initializing", "platform services");
		PackForgeServices.platform().logPlatformInfo();
		StartupStatus.update("Loading", "PackForge config");
		long configStartNs = System.nanoTime();
		PackForgeConfig.load();
		StartupTimings.recordDuration("packforge_config_load", System.nanoTime() - configStartNs);
		StartupTimings.event("packforge_config_loaded");
		StartupStatus.update("Configuring", "startup optimizer");
		PackForge.LOGGER.info("PackForge initialized (loader={}, loaderIndex={}, atlasCap={}, atlasRetry={}, startupOptimizer={}, startupExecutorTuning={})",
			PackForgeServices.platform().loaderName(),
			PackForgeConfig.get().loaderIndexEnabled,
			PackForgeConfig.get().atlasCapEnabled,
			PackForgeConfig.get().atlasRetryEnabled,
			PackForgeConfig.get().startupOptimizerEnabled,
			PackForgeConfig.get().startupExecutorTuningEnabled);
		if (PackForgeConfig.get().startupOptimizerEnabled && PackForgeConfig.get().startupExecutorTuningEnabled) {
			PackForge.LOGGER.info("PackForge startup: executor tuning is applied during early client bootstrap; changes require a restart to affect the next run");
		}
		StartupAsyncFeatures.startConfiguredWork();
		StartupTimings.mark("core_init");
		StartupStatus.update("Waiting for", "Minecraft bootstrap");
	}

	private PackForgeCore() {}
}
