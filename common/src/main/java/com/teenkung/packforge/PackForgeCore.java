package com.teenkung.packforge;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.PackForgeCapabilities;
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
		var codeSource = PackForgeCore.class.getProtectionDomain().getCodeSource();
		PackForge.LOGGER.info("PackForge runtime source: {}", codeSource == null ? "unknown" : codeSource.getLocation());
		StartupStatus.update("Loading", "PackForge config");
		long configStartNs = System.nanoTime();
		PackForgeConfig.load();
		StartupTimings.recordDuration("packforge_config_load", System.nanoTime() - configStartNs);
		StartupTimings.event("packforge_config_loaded");
		PackForge.LOGGER.info("PackForge capabilities: target={} available={} unavailable={}",
			PackForgeCapabilities.target(), PackForgeCapabilities.available(), PackForgeCapabilities.unavailable());
		if (!PackForgeCapabilities.unknownIdentifiers().isEmpty()) {
			PackForge.LOGGER.warn("PackForge capability profile contains unknown identifiers: {}", PackForgeCapabilities.unknownIdentifiers());
		}
		StartupStatus.update("Configuring", "startup optimizer");
		PackForge.LOGGER.info("PackForge initialized (loader={}, loaderIndex={}, atlasCap={}, atlasRetry={}, startupOptimizer={}, startupExecutorTuning={})",
			PackForgeServices.platform().loaderName(),
			FeatureFlags.loaderIndexEnabled(),
			FeatureFlags.atlasCapEnabled(),
			FeatureFlags.atlasRetryEnabled(),
			FeatureFlags.startupOptimizerEnabled(),
			FeatureFlags.startupExecutorTuningEnabled());
		if (FeatureFlags.startupExecutorTuningEnabled()) {
			PackForge.LOGGER.info("PackForge startup: executor tuning is applied during early client bootstrap; changes require a restart to affect the next run");
		}
		StartupAsyncFeatures.startConfiguredWork();
		StartupTimings.mark("core_init");
		StartupStatus.update("Waiting for", "Minecraft bootstrap");
	}

	private PackForgeCore() {}
}
