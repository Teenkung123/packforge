package com.teenkung.packforge.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
