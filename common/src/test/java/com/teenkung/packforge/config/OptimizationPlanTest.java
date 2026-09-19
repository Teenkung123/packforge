package com.teenkung.packforge.config;

import com.teenkung.packforge.platform.OptimizationCompatibility;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.teenkung.packforge.config.PackForgeCapability.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationPlanTest {
	private static final Set<PackForgeCapability> ALL = EnumSet.allOf(PackForgeCapability.class);
	private static final OptimizationCompatibility.State STANDALONE = new OptimizationCompatibility.State(false, false, false);


	@Test
	void cpuPreparationDoesNotEnableResizingOrChangeSavedChoices() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.largeAtlasFixerEnabled = false;
		config.cpuMipPreparationEnabled = true;
		config.atlasCapEnabled = true;
		config.fontBitmapProviderCacheEnabled = true;
		OptimizationPlan plan = OptimizationPlan.create(config, ALL, "mc26_1_to_26_2", STANDALONE);

		assertTrue(plan.enabled(ATLAS_MIP_PARALLEL));
		assertFalse(plan.enabled(ATLAS_CAP));
		assertEquals(OptimizationPlan.Owner.MINECRAFT, plan.decision(ATLAS_CAP).owner());
		assertTrue(plan.decision(ATLAS_CAP).requested());
		assertTrue(plan.decision(ATLAS_CAP).reason().contains("Original texture dimensions are preserved"));
		assertFalse(plan.enabled(FONT_BITMAP_CACHE));
		assertTrue(plan.decision(FONT_BITMAP_CACHE).requested());
		assertTrue(plan.decision(FONT_BITMAP_CACHE).reason().contains("starved"));
		assertTrue(config.fontBitmapProviderCacheEnabled);
		assertFalse(config.largeAtlasFixerEnabled);
	}

	@Test
	void atlasCapAdmissionPreservesOriginalDimensionsEvenWhenRequested() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.largeAtlasFixerEnabled = true;
		config.atlasCapEnabled = true;
		config.atlasCapPx = 256;
		OptimizationPlan plan = OptimizationPlan.create(config, ALL, "mc26_1_to_26_3", STANDALONE);

		OptimizationPlan.Decision decision = plan.decision(ATLAS_CAP);
		assertTrue(decision.requested());
		assertFalse(decision.effective());
		assertEquals(OptimizationPlan.Owner.MINECRAFT, decision.owner());
		assertEquals("Original texture dimensions are preserved; atlas sprite cap is not admitted", decision.reason());
		assertTrue(config.atlasCapEnabled);
		assertEquals(256, config.atlasCapPx);
	}

	@Test
	void quickPackOwnsAllOverlappingPathsIncludingStartupShortcuts() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.fontBitmapProviderCacheEnabled = true;
		config.loaderZipPoolEnabled = true;
		config.resourceReadReuseEnabled = true;
		config.cpuMipPreparationEnabled = true;
		config.startupOptimizerEnabled = true;
		config.startupExecutorTuningEnabled = true;
		config.startupAsyncDataParsingEnabled = true;
		config.startupAsyncFontAtlasEnabled = true;
		OptimizationPlan plan = OptimizationPlan.create(config, ALL, "mc26_1_to_26_2",
			new OptimizationCompatibility.State(true, false, false));

		for (PackForgeCapability stage : Set.of(FONT_PROVIDER_PRESELECTION, FONT_BITMAP_CACHE,
			ATLAS_MIP_PARALLEL, ATLAS_DECODE_BATCHING, RESOURCE_PACK_INDEX, ZIP_READ_POOL,
			RESOURCE_READ_REUSE, MODEL_PARSE_BATCHING, MODEL_ADAPTIVE_BATCHING, MODEL_DUPLICATE_CACHE,
			STARTUP_EXECUTOR_TUNING, STARTUP_ASYNC_DATA, STARTUP_ASYNC_FONT_ATLAS,
			LOADING_FADE_CONTROL, LOADING_STATUS_OVERLAY)) {
			assertFalse(plan.enabled(stage), stage.name());
			assertEquals(OptimizationPlan.Owner.QUICK_PACK, plan.decision(stage).owner(), stage.name());
		}
		assertTrue(config.startupAsyncFontAtlasEnabled);
		assertTrue(config.resourceReadReuseEnabled);
	}

	@Test
	void rrlsOwnsOverlayAndPlatformApiKeepsCustomDispatch() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.loadingScreenFadeOutDisabled = true;
		OptimizationPlan plan = OptimizationPlan.create(config, ALL, "mc26_1_to_26_2",
			new OptimizationCompatibility.State(false, true, true));
		assertEquals(OptimizationPlan.Owner.RRLS, plan.decision(LOADING_STATUS_OVERLAY).owner());
		assertEquals(OptimizationPlan.Owner.RRLS, plan.decision(LOADING_FADE_CONTROL).owner());
		assertEquals(OptimizationPlan.Owner.PLATFORM, plan.decision(MODEL_PARSE_BATCHING).owner());
		assertFalse(plan.enabled(MODEL_PARSE_BATCHING));
		assertTrue(plan.enabled(FONT_PROVIDER_PRESELECTION));
	}

	@Test
	void shaderGuardPreservesTheRequestedRetrySettingAndCanBeOverridden() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.largeAtlasFixerEnabled = true;
		config.atlasRetryEnabled = true;
		config.forceDisablePartIIIWithIris = true;
		OptimizationCompatibility.State iris = new OptimizationCompatibility.State(false, false, false, true);
		OptimizationPlan plan = OptimizationPlan.create(config, ALL, "mc26_1_to_26_2", iris);
		assertTrue(plan.decision(ATLAS_RETRY).requested());
		assertFalse(plan.enabled(ATLAS_RETRY));
		assertTrue(plan.decision(ATLAS_RETRY).reason().contains("Iris/Oculus"));
		assertTrue(config.atlasRetryEnabled);
		config.forceDisablePartIIIWithIris = false;
		assertTrue(OptimizationPlan.create(config, ALL, "mc26_1_to_26_2", iris).enabled(ATLAS_RETRY));
	}

	@Test
	void planFreezesOptionsAndCapabilitiesForTheWholeReload() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		Set<PackForgeCapability> available = EnumSet.copyOf(ALL);
		OptimizationPlan plan = OptimizationPlan.create(config, available, "mc26_1_to_26_2", STANDALONE);
		config.fontPrepareProviderSelectionEnabled = false;
		available.clear();
		assertTrue(plan.enabled(FONT_PROVIDER_PRESELECTION));
		assertThrows(UnsupportedOperationException.class, () -> plan.decisions().clear());
		assertFalse(OptimizationPlan.create(config, available, "mc26_1_to_26_2", STANDALONE)
			.enabled(FONT_PROVIDER_PRESELECTION));
	}

	@Test
	void withdrawnBitmapPolicyPreservesRequestAndExplainsTargetOwnership() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.fontBitmapProviderCacheEnabled = true;
		OptimizationPlan legacy = OptimizationPlan.create(config, ALL, "mc1_20_1", STANDALONE);
		assertFalse(legacy.enabled(FONT_BITMAP_CACHE));
		assertEquals(OptimizationPlan.Owner.MINECRAFT, legacy.decision(FONT_BITMAP_CACHE).owner());
		assertTrue(legacy.decision(FONT_BITMAP_CACHE).requested());
		assertTrue(legacy.decision(FONT_BITMAP_CACHE).reason().contains("coordinated font preparation"));
		OptimizationPlan port = OptimizationPlan.create(config, ALL, "mc1_21_11", STANDALONE);
		assertFalse(port.enabled(FONT_BITMAP_CACHE));
		assertTrue(port.decision(FONT_BITMAP_CACHE).requested());
		assertTrue(config.fontBitmapProviderCacheEnabled);
		assertFalse(OptimizationPlan.create(config, ALL, "mc26_3", STANDALONE).enabled(FONT_BITMAP_CACHE));
	}

	@Test
	void disabledMastersAndUnsupportedCapabilitiesNeverActivateShortcuts() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.reloadOptimizerEnabled = false;
		config.startupOptimizerEnabled = false;
		config.startupAsyncFontAtlasEnabled = true;
		config.cpuMipPreparationEnabled = false;
		OptimizationPlan plan = OptimizationPlan.create(config, ALL, "mc26_1_to_26_2", STANDALONE);
		assertFalse(plan.enabled(FONT_PROVIDER_PRESELECTION));
		assertFalse(plan.enabled(ATLAS_MIP_PARALLEL));
		config.startupOptimizerEnabled = true;
		plan = OptimizationPlan.create(config, Set.of(FONT_PROVIDER_PRESELECTION), "mc26_1_to_26_2", STANDALONE);
		assertFalse(plan.enabled(FONT_PROVIDER_PRESELECTION));
	}
}
