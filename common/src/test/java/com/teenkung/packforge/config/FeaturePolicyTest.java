package com.teenkung.packforge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_MIP_PARALLEL;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_PROVIDER_PRESELECTION;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_FADE_CONTROL;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_STATUS_OVERLAY;
import static com.teenkung.packforge.config.PackForgeCapability.RESOURCE_PACK_INDEX;
import static com.teenkung.packforge.config.PackForgeCapability.ZIP_READ_POOL;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class FeaturePolicyTest {
	@Test
	void capabilityProfileIsTheOnlyFeatureSupportAuthority() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.startupOptimizerEnabled = true;
		Properties properties = new Properties();
		properties.setProperty("target", "test");
		properties.setProperty("capabilities", "RESOURCE_PACK_INDEX,STARTUP_OPTIMIZER");

		FeaturePolicy policy = FeaturePolicy.forTesting(config, PackForgeCapabilityProfile.fromProperties(properties));

		assertTrue(policy.loaderIndexEnabled());
		assertFalse(policy.loaderZipPoolEnabled());
		assertTrue(policy.startupOptimizerEnabled());
		assertFalse(policy.atlasCapEnabled());
	}

	@Test
	void policyCopiesMutableConfigAtTheBoundary() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		FeaturePolicy policy = FeaturePolicy.forTesting(config, PackForgeCapabilityProfile.currentDevelopment());

		config.reloadOptimizerEnabled = false;

		assertTrue(policy.reloadOptimizerEnabled());
		assertTrue(policy.modelParseBatchingEnabled());
	}

	@Test
	void quickPackOwnsOnlyTheUnprovenOverlapSurface() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		Properties properties = new Properties();
		properties.setProperty("target", "test");
		properties.setProperty("capabilities", "RESOURCE_PACK_INDEX,ZIP_READ_POOL,FONT_PROVIDER_PRESELECTION,ATLAS_MIP_PARALLEL,LOADING_FADE_CONTROL,LOADING_STATUS_OVERLAY,MODEL_PARSE_BATCHING");

		FeaturePolicy policy = FeaturePolicy.forTesting(
			config,
			PackForgeCapabilityProfile.fromProperties(properties),
			QuickPackCompatibility.forTesting("1.5.7")
		);

		assertTrue(policy.quickPackLoaded());
		assertTrue(policy.quickPackOwns(RESOURCE_PACK_INDEX));
		assertTrue(policy.quickPackOwns(ZIP_READ_POOL));
		assertFalse(policy.loaderIndexEnabled());
		assertFalse(policy.loaderZipPoolEnabled());
		assertFalse(policy.fontPrepareProviderSelectionEnabled());
		assertFalse(policy.atlasMipParallelEnabled());
		assertFalse(policy.loadingScreenFadeOutDisabled());
		assertFalse(policy.loadingStatusOverlayEnabled());
		assertTrue(policy.modelParseBatchingEnabled());
		assertTrue(policy.quickPackDisabledReason(RESOURCE_PACK_INDEX).contains("1.5.x"));
	}

	@Test
	void unknownQuickPackVersionFailsClosedWithoutThrowing() {
		QuickPackCompatibility.Profile profile = QuickPackCompatibility.forTesting("not-a-version");

		assertEquals(QuickPackCompatibility.Status.UNKNOWN_VERSION, profile.status());
		assertTrue(profile.owns(RESOURCE_PACK_INDEX));
		assertTrue(profile.owns(ZIP_READ_POOL));
		assertTrue(profile.owns(FONT_PROVIDER_PRESELECTION));
		assertTrue(profile.owns(ATLAS_MIP_PARALLEL));
		assertTrue(profile.owns(LOADING_FADE_CONTROL));
		assertTrue(profile.owns(LOADING_STATUS_OVERLAY));
	}
}
