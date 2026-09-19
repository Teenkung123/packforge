package com.teenkung.packforge.config;

import com.teenkung.packforge.platform.OptimizationCompatibility;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReloadFeedbackSnapshotTest {
	@Test
	void externalLiveFeedbackTracksReloadWithoutSummaryOrProfiling() {
		for (OptimizationCompatibility.State compatibility : new OptimizationCompatibility.State[] {
			new OptimizationCompatibility.State(true, false, false),
			new OptimizationCompatibility.State(false, true, false),
			new OptimizationCompatibility.State(true, true, false)
		}) {
			PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
			config.loadingStatusOverlayEnabled = true;
			ReloadFeatureSnapshot snapshot = snapshot(config, compatibility);
			assertTrue(snapshot.externalFeedbackEnabled());
			assertTrue(snapshot.statusTrackingEnabled());
			assertFalse(snapshot.reloadSummaryToastEnabled());
			assertFalse(snapshot.detailedTaskTelemetryEnabled());
			config.loadingStatusOverlayEnabled = false;
			assertTrue(snapshot.externalFeedbackEnabled(), "An active reload retains its requested display choice");
			ReloadFeatureSnapshot next = snapshot(config, compatibility);
			assertFalse(next.externalFeedbackEnabled());
			assertFalse(next.statusTrackingEnabled());
		}
	}

	@Test
	void standaloneUsesItsOverlayAndDisabledStatusStaysSilent() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		OptimizationCompatibility.State standalone = new OptimizationCompatibility.State(false, false, false);
		config.loadingStatusOverlayEnabled = true;
		assertFalse(snapshot(config, standalone).externalFeedbackEnabled());
		config.loadingStatusOverlayEnabled = false;
		ReloadFeatureSnapshot disabled = snapshot(config, standalone);
		assertFalse(disabled.externalFeedbackEnabled());
		assertFalse(disabled.statusTrackingEnabled());
	}

	@Test
	void externalOwnershipCannotBypassDisabledReloadMaster() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.loadingStatusOverlayEnabled = true;
		config.reloadOptimizerEnabled = false;
		ReloadFeatureSnapshot snapshot = snapshot(config, new OptimizationCompatibility.State(true, true, false));
		assertFalse(snapshot.externalFeedbackEnabled());
		assertFalse(snapshot.statusTrackingEnabled());
	}

	private static ReloadFeatureSnapshot snapshot(PackForgeConfig.Cfg config, OptimizationCompatibility.State compatibility) {
		OptimizationPlan plan = OptimizationPlan.create(config, EnumSet.allOf(PackForgeCapability.class),
			"mc26_1_to_26_2", compatibility);
		return new ReloadFeatureSnapshot(
			config.reloadOptimizerEnabled, false, false, false, false, false, false, plan.enabled(PackForgeCapability.LOADING_STATUS_OVERLAY), false, false,
			false, false, false, 64, false, false, false, false, false, false,
			false, false, 128, false, 128, false, 256, Set.of(), false, 2,
			false, false, false, false, false, false, false, 1, 4, true,
			false, false, false, false, 1, false, 128, plan);
	}
}
