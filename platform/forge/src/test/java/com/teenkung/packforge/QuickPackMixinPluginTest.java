package com.teenkung.packforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickPackMixinPluginTest {
	private static final String STANDARD =
		"com.teenkung.packforge.mixin.observe.ReloadableResourceManagerMixin";
	private static final String LEGACY =
		"com.teenkung.packforge.mixin.observe.ForgeLegacyReloadableResourceManagerMixin";

	@Test
	void selectsStandardObserverForForge48() {
		assertTrue(QuickPackMixinPlugin.shouldApplyReloadObserver(STANDARD, "1.20.2"));
		assertFalse(QuickPackMixinPlugin.shouldApplyReloadObserver(LEGACY, "1.20.2"));
	}

	@Test
	void selectsLegacyObserverForForge49() {
		for (String minecraftVersion : new String[] {"1.20.3", "1.20.4"}) {
			assertFalse(QuickPackMixinPlugin.shouldApplyReloadObserver(STANDARD, minecraftVersion));
			assertTrue(QuickPackMixinPlugin.shouldApplyReloadObserver(LEGACY, minecraftVersion));
		}
	}

	@Test
	void preservesStandardObserverOutsideTheLegacySplit() {
		assertTrue(QuickPackMixinPlugin.shouldApplyReloadObserver(STANDARD, "1.21.4"));
		assertTrue(QuickPackMixinPlugin.shouldApplyReloadObserver(STANDARD, ""));
		assertFalse(QuickPackMixinPlugin.shouldApplyReloadObserver(LEGACY, ""));
		assertTrue(QuickPackMixinPlugin.shouldApplyReloadObserver("example.UnrelatedMixin", "1.20.3"));
	}
}
