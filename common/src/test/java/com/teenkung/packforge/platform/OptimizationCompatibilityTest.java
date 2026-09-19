package com.teenkung.packforge.platform;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationCompatibilityTest {
	@Test
	void recognizesFabricAndForgeFamilyQuickPackMetadataIds() {
		assertTrue(OptimizationCompatibility.fromDiscovery("fabric", Set.of("quick-pack")::contains).quickPackPresent());
		assertTrue(OptimizationCompatibility.fromDiscovery("forge", Set.of("quick_pack")::contains).quickPackPresent());
		assertTrue(OptimizationCompatibility.fromDiscovery("neoforge", Set.of("quick_pack")::contains).quickPackPresent());
		assertFalse(OptimizationCompatibility.fromDiscovery("fabric", Set.of("quickpack")::contains).quickPackPresent());
	}

	@Test
	void capturesIndependentOwnersWithoutLookingAtFilenames() {
		var rrls = OptimizationCompatibility.fromDiscovery("fabric", Set.of("rrls")::contains);
		assertTrue(rrls.rrlsPresent());
		assertFalse(rrls.quickPackPresent());
		assertFalse(rrls.preservePlatformModelLoading());
		var all = OptimizationCompatibility.fromDiscovery("fabric",
			Set.of("quick-pack", "rrls", "fabric-model-loading-api-v1")::contains);
		assertTrue(all.quickPackPresent());
		assertTrue(all.rrlsPresent());
		assertTrue(all.preservePlatformModelLoading());
	}

	@Test
	void onlyFabricOwnsTheFabricModelLoadingBridge() {
		var api = Set.of("fabric-model-loading-api-v1");
		assertTrue(OptimizationCompatibility.fromDiscovery("fabric", api::contains).preservePlatformModelLoading());
		assertFalse(OptimizationCompatibility.fromDiscovery("forge", api::contains).preservePlatformModelLoading());
		assertFalse(OptimizationCompatibility.fromDiscovery("neoforge", api::contains).preservePlatformModelLoading());
	}
}
