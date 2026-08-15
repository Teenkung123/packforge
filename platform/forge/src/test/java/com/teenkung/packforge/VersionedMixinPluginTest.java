package com.teenkung.packforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedMixinPluginTest {
	private static final String STANDARD =
		"com.teenkung.packforge.mixin.observe.ReloadableResourceManagerMixin";
	private static final String LEGACY =
		"com.teenkung.packforge.mixin.observe.ForgeLegacyReloadableResourceManagerMixin";

	@Test
	void selectsStandardObserverForForge48() {
		assertTrue(VersionedMixinPlugin.shouldApplyReloadObserver(STANDARD, "1.20.2"));
		assertFalse(VersionedMixinPlugin.shouldApplyReloadObserver(LEGACY, "1.20.2"));
	}

	@Test
	void selectsLegacyObserverForForge49() {
		for (String minecraftVersion : new String[] {"1.20.3", "1.20.4"}) {
			assertFalse(VersionedMixinPlugin.shouldApplyReloadObserver(STANDARD, minecraftVersion));
			assertTrue(VersionedMixinPlugin.shouldApplyReloadObserver(LEGACY, minecraftVersion));
		}
	}

	@Test
	void preservesStandardObserverOutsideTheLegacySplit() {
		assertTrue(VersionedMixinPlugin.shouldApplyReloadObserver(STANDARD, "1.21.4"));
		assertTrue(VersionedMixinPlugin.shouldApplyReloadObserver(STANDARD, ""));
		assertFalse(VersionedMixinPlugin.shouldApplyReloadObserver(LEGACY, ""));
		assertTrue(VersionedMixinPlugin.shouldApplyReloadObserver("example.UnrelatedMixin", "1.20.3"));
	}
}
