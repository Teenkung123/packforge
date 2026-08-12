package com.teenkung.packforge.compat;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedMixinGateTest {
	@Test
	void disablesOnlyMixinsBelowTheirDeclaredFloor() {
		Properties properties = new Properties();
		properties.setProperty("mixinFloor.client.mixin.atlas.SpriteLoaderMixin", "1.21.11");

		assertFalse(VersionedMixinGate.shouldApply(
			"com.teenkung.packforge.client.mixin.atlas.SpriteLoaderMixin", "1.21.10", properties));
		assertTrue(VersionedMixinGate.shouldApply(
			"com.teenkung.packforge.client.mixin.atlas.SpriteLoaderMixin", "1.21.11", properties));
		assertTrue(VersionedMixinGate.shouldApply(
			"com.teenkung.packforge.mixin.observe.ReloadableResourceManagerMixin", "1.21.9", properties));
	}

	@Test
	void missingRuntimeVersionFailsClosedForFlooredMixin() {
		Properties properties = new Properties();
		properties.setProperty("mixinFloor.client.mixin.model.ModelManagerMixin", "1.21.11");
		assertFalse(VersionedMixinGate.shouldApply(
			"com.teenkung.packforge.client.mixin.model.ModelManagerMixin", "", properties));
	}
}
