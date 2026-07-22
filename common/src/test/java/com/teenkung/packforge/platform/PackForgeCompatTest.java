package com.teenkung.packforge.platform;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeCompatTest {
	@Test
	void preservesFabricModelLoadingPipelineWhenApiIsPresent() {
		Set<String> loadedMods = Set.of("fabric-api", "fabric-model-loading-api-v1");

		assertTrue(PackForgeCompat.mustPreservePlatformModelLoading("fabric", loadedMods::contains));
	}

	@Test
	void keepsOptimizerAvailableWithoutFabricModelLoadingApi() {
		assertFalse(PackForgeCompat.mustPreservePlatformModelLoading("fabric", Set.<String>of()::contains));
		assertFalse(PackForgeCompat.mustPreservePlatformModelLoading("forge", modId -> true));
		assertFalse(PackForgeCompat.mustPreservePlatformModelLoading("neoforge", modId -> true));
	}
}
