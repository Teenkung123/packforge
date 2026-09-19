package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeCapability;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.config.OptimizationPlan;
import com.teenkung.packforge.platform.OptimizationCompatibility;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeConfigScreenModelTest {
	private static final EnumSet<PackForgeCapability> LEGACY = EnumSet.of(
		PackForgeCapability.RESOURCE_PACK_INDEX,
		PackForgeCapability.ZIP_READ_POOL,
		PackForgeCapability.LOADER_TIMINGS
	);

	@Test
	void legacyProfileShowsOnlyEffectiveReloadOptions() {
		List<PackForgeConfigScreenModel.OptionSpec> options = PackForgeConfigScreenModel.availableOptions(LEGACY);

		assertEquals(List.of("reload_optimizer", "loader_index", "loader_zip_pool", "loader_timings"),
			options.stream().map(PackForgeConfigScreenModel.OptionSpec::id).toList());
		assertEquals(List.of(PackForgeConfigScreenModel.Category.RELOAD),
			PackForgeConfigScreenModel.availableCategories(LEGACY));
	}

	@Test
	void currentProfileDoesNotExposeReservedAtlasSplitSettings() {
		List<String> identifiers = PackForgeConfigScreenModel.availableOptions(EnumSet.allOf(PackForgeCapability.class))
			.stream()
			.map(PackForgeConfigScreenModel.OptionSpec::id)
			.toList();

		assertTrue(identifiers.contains("atlas_cap"));
		assertTrue(identifiers.contains("startup_optimizer"));
		assertFalse(identifiers.stream().anyMatch(identifier -> identifier.contains("split")));
	}

	@Test
	void reloadOnlyProfileCanExposeEveryReloadOptionWithoutAtlasOrStartup() {
		EnumSet<PackForgeCapability> reload = EnumSet.of(
			PackForgeCapability.RESOURCE_PACK_INDEX, PackForgeCapability.ZIP_READ_POOL, PackForgeCapability.LOADER_TIMINGS,
			PackForgeCapability.RELOAD_LISTENER_TIMINGS, PackForgeCapability.SHADER_STALL_DIAGNOSTICS,
			PackForgeCapability.IMMEDIATELY_FAST_FONT_ATLAS_COMPAT, PackForgeCapability.LOADING_STATUS_OVERLAY,
			PackForgeCapability.LOADING_FADE_CONTROL, PackForgeCapability.RELOAD_SUMMARY_TOAST,
			PackForgeCapability.FONT_RELOAD_DIAGNOSTICS, PackForgeCapability.FONT_PROVIDER_PRESELECTION,
			PackForgeCapability.FONT_BITMAP_CACHE, PackForgeCapability.MODEL_PARSE_BATCHING,
			PackForgeCapability.MODEL_PARSE_TIMINGS, PackForgeCapability.MODEL_ADAPTIVE_BATCHING,
			PackForgeCapability.MODEL_DUPLICATE_CACHE, PackForgeCapability.ATLAS_PHASE_TIMINGS,
			PackForgeCapability.ATLAS_DECODE_BATCHING);
		List<PackForgeConfigScreenModel.OptionSpec> options = PackForgeConfigScreenModel.availableOptions(reload);
		assertEquals(21, options.size());
		assertTrue(options.stream().allMatch(option -> option.category() == PackForgeConfigScreenModel.Category.RELOAD));
	}

	@Test
	void mc120ReloadProfileOmitsFontProviderPreselection() {
		EnumSet<PackForgeCapability> oldReload = EnumSet.of(
			PackForgeCapability.RESOURCE_PACK_INDEX, PackForgeCapability.ZIP_READ_POOL, PackForgeCapability.LOADER_TIMINGS,
			PackForgeCapability.RELOAD_LISTENER_TIMINGS, PackForgeCapability.SHADER_STALL_DIAGNOSTICS,
			PackForgeCapability.IMMEDIATELY_FAST_FONT_ATLAS_COMPAT, PackForgeCapability.LOADING_STATUS_OVERLAY,
			PackForgeCapability.LOADING_FADE_CONTROL, PackForgeCapability.RELOAD_SUMMARY_TOAST,
			PackForgeCapability.FONT_RELOAD_DIAGNOSTICS, PackForgeCapability.FONT_BITMAP_CACHE,
			PackForgeCapability.MODEL_PARSE_BATCHING, PackForgeCapability.MODEL_PARSE_TIMINGS,
			PackForgeCapability.MODEL_ADAPTIVE_BATCHING, PackForgeCapability.MODEL_DUPLICATE_CACHE,
			PackForgeCapability.ATLAS_PHASE_TIMINGS, PackForgeCapability.ATLAS_DECODE_BATCHING);
		List<PackForgeConfigScreenModel.OptionSpec> options = PackForgeConfigScreenModel.availableOptions(oldReload);
		assertEquals(20, options.size());
		assertEquals(List.of(PackForgeConfigScreenModel.Category.RELOAD),
			PackForgeConfigScreenModel.availableCategories(oldReload));
		assertFalse(options.stream()
			.anyMatch(option -> option.id().equals("font_provider_selection")));
	}

	@Test
	void legacyResetPreservesUnsupportedCurrentValues() {
		PackForgeConfig.Cfg source = new PackForgeConfig.Cfg();
		source.loaderIndexEnabled = false;
		source.atlasCapPx = 1024;
		source.atlasRetryEnabled = true;
		PackForgeConfigDraft draft = new PackForgeConfigDraft(source);

		draft.resetAll(PackForgeConfigScreenModel.availableOptions(LEGACY));

		assertTrue(draft.working().loaderIndexEnabled);
		assertEquals(1024, draft.working().atlasCapPx);
		assertTrue(draft.working().atlasRetryEnabled);
	}

	@Test
	void reworkControlsAreCapabilityGatedAndMemoryBudgetIsValidated() {
		List<PackForgeConfigScreenModel.OptionSpec> options = PackForgeConfigScreenModel.availableOptions(
			EnumSet.of(PackForgeCapability.RESOURCE_READ_REUSE), "mc26_1_to_26_2");
		assertEquals(List.of("resource_read_reuse", "optimization_memory_mib"), options.stream().map(PackForgeConfigScreenModel.OptionSpec::id).toList());
		var reuse = (PackForgeConfigScreenModel.BooleanOption) options.get(0);
		var memory = (PackForgeConfigScreenModel.IntegerOption) options.get(1);
		PackForgeConfig.Cfg cfg = new PackForgeConfig.Cfg();
		assertFalse(reuse.get(cfg));
		assertEquals(128, memory.get(cfg));
		assertEquals("advanced", memory.section());
		assertEquals(PackForgeConfigScreenModel.ApplyScope.RESOURCE_RELOAD, memory.applyScope());
		assertThrows(IllegalArgumentException.class, () -> memory.set(cfg, 0));
		assertThrows(IllegalArgumentException.class, () -> memory.set(cfg, 129));
		reuse.set(cfg, true);
		memory.set(cfg, 1);
		PackForgeConfig.Cfg original = new PackForgeConfig.Cfg();
		assertFalse(PackForgeConfigScreenModel.sameValues(original, cfg));
		reuse.reset(cfg);
		memory.reset(cfg);
		assertTrue(PackForgeConfigScreenModel.sameValues(original, cfg));
	}

	@Test
	void mc26HidesUnusedBatchSizesAndResetPreservesTheirStoredValues() {
		var capabilities = EnumSet.of(PackForgeCapability.ATLAS_MIP_PARALLEL,
			PackForgeCapability.ATLAS_DECODE_BATCHING, PackForgeCapability.MODEL_PARSE_BATCHING);
		for (String target : List.of("mc26", "mc26_1_to_26_2", "mc26_3")) {
			var options = PackForgeConfigScreenModel.availableOptions(capabilities, target);
			assertEquals(List.of("model_parse_batching", "atlas_decode_batching", "cpu_mip_preparation"),
				options.stream().map(PackForgeConfigScreenModel.OptionSpec::id).toList());
			PackForgeConfig.Cfg cfg = new PackForgeConfig.Cfg();
			cfg.atlasMipBatchSize = 512;
			cfg.atlasDecodeBatchSize = 256;
			cfg.modelParseBatchSize = 32;
			PackForgeConfigDraft draft = new PackForgeConfigDraft(cfg);
			draft.resetAll(options);
			assertEquals(512, draft.working().atlasMipBatchSize);
			assertEquals(256, draft.working().atlasDecodeBatchSize);
			assertEquals(32, draft.working().modelParseBatchSize);
		}
		var legacyOptions = PackForgeConfigScreenModel.availableOptions(capabilities, "mc1_21_11");
		assertEquals(List.of("model_parse_batching", "model_parse_batch_size", "atlas_decode_batching",
			"atlas_decode_batch_size", "cpu_mip_preparation", "atlas_mip_batch_size"),
			legacyOptions.stream().map(PackForgeConfigScreenModel.OptionSpec::id).toList());
		PackForgeConfig.Cfg legacy = new PackForgeConfig.Cfg();
		legacy.atlasMipBatchSize = 512;
		legacy.atlasDecodeBatchSize = 256;
		legacy.modelParseBatchSize = 32;
		PackForgeConfigDraft legacyDraft = new PackForgeConfigDraft(legacy);
		legacyDraft.resetAll(legacyOptions);
		assertEquals(128, legacyDraft.working().atlasMipBatchSize);
		assertEquals(128, legacyDraft.working().atlasDecodeBatchSize);
		assertEquals(64, legacyDraft.working().modelParseBatchSize);
	}

	@Test
	void recommendationPreviewIsDetachedCompleteAndPreservesQualityAndFadeChoices() {
		PackForgeConfig.Cfg source = new PackForgeConfig.Cfg();
		source.reloadOptimizerEnabled = false;
		source.loaderIndexEnabled = false;
		source.fontPrepareProviderSelectionEnabled = false;
		source.cpuMipPreparationEnabled = false;
		source.atlasMipParallelEnabled = true;
		source.loaderZipPoolEnabled = true;
		source.resourceReadReuseEnabled = true;
		source.fontBitmapProviderCacheEnabled = true;
		source.loaderTimingsEnabled = true;
		source.shaderApplyStallDiagnosticsEnabled = true;
		source.startupExecutorTuningEnabled = true;
		source.startupWorkerThreads = 2;
		source.startupThreadPriority = 7;
		source.largeAtlasFixerEnabled = false;
		source.atlasCapEnabled = true;
		source.atlasCapPx = 512;
		source.atlasRetryEnabled = true;
		source.loadingScreenFadeOutDisabled = true;
		source.loadingStatusOverlayEnabled = false;
		var options = PackForgeConfigScreenModel.availableOptions(EnumSet.allOf(PackForgeCapability.class), "mc26_1_to_26_2");
		var preview = PackForgeConfigScreenModel.recommendedChanges(source, options);
		assertFalse(source.cpuMipPreparationEnabled);
		assertTrue(source.fontBitmapProviderCacheEnabled);
		var previewValues = PackForgeConfigScreenModel.previewRecommendations(source, preview);
		assertTrue(previewValues.cpuMipPreparationEnabled);
		assertFalse(previewValues.fontBitmapProviderCacheEnabled);
		assertFalse(source.cpuMipPreparationEnabled);
		assertTrue(source.fontBitmapProviderCacheEnabled);
		assertThrows(UnsupportedOperationException.class, () -> preview.clear());
		assertEquals(List.of("reload_optimizer", "loader_index", "loader_zip_pool", "resource_read_reuse",
			"font_provider_selection", "font_bitmap_cache", "loader_timings", "shader_stall_diagnostics",
			"cpu_mip_preparation", "startup_executor_tuning"), preview.stream().map(change -> change.option().id()).toList());
		PackForgeConfigDraft draft = new PackForgeConfigDraft(source);
		for (var change : preview) change.apply(draft.working());
		assertTrue(draft.dirty());
		assertTrue(draft.working().cpuMipPreparationEnabled);
		assertTrue(draft.working().reloadOptimizerEnabled);
		assertTrue(draft.working().loaderIndexEnabled);
		assertTrue(draft.working().fontPrepareProviderSelectionEnabled);
		assertFalse(draft.working().fontBitmapProviderCacheEnabled);
		assertFalse(draft.working().startupExecutorTuningEnabled);
		assertFalse(draft.working().largeAtlasFixerEnabled);
		assertTrue(draft.working().atlasCapEnabled);
		assertEquals(512, draft.working().atlasCapPx);
		assertTrue(draft.working().atlasRetryEnabled);
		assertTrue(draft.working().loadingScreenFadeOutDisabled);
		assertFalse(draft.working().loadingStatusOverlayEnabled);
		assertTrue(draft.working().atlasMipParallelEnabled);
		assertEquals(2, draft.working().startupWorkerThreads);
		assertEquals(7, draft.working().startupThreadPriority);
		assertTrue(PackForgeConfigScreenModel.recommendedChanges(draft.working(), options).isEmpty());
		draft.discard();
		assertTrue(PackForgeConfigScreenModel.sameValues(source, draft.working()));
	}

	@Test
	void recommendationOnLegacyTargetPreservesUnsupportedSettings() {
		PackForgeConfig.Cfg source = new PackForgeConfig.Cfg();
		source.loaderZipPoolEnabled = true;
		source.cpuMipPreparationEnabled = false;
		source.fontBitmapProviderCacheEnabled = true;
		var changes = PackForgeConfigScreenModel.recommendedChanges(source, PackForgeConfigScreenModel.availableOptions(LEGACY));
		for (var change : changes) change.apply(source);
		assertFalse(source.loaderZipPoolEnabled);
		assertFalse(source.cpuMipPreparationEnabled);
		assertTrue(source.fontBitmapProviderCacheEnabled);
	}

	@Test
	void cpuMipControlIsIndependentFromResizingAndDoesNotRewriteLegacyAlias() {
		var option = (PackForgeConfigScreenModel.BooleanOption) PackForgeConfigScreenModel.allOptions().stream()
			.filter(value -> value.id().equals("cpu_mip_preparation")).findFirst().orElseThrow();
		PackForgeConfig.Cfg cfg = new PackForgeConfig.Cfg();
		cfg.cpuMipPreparationEnabled = false;
		cfg.largeAtlasFixerEnabled = false;
		cfg.atlasMipParallelEnabled = false;
		option.set(cfg, true);
		assertTrue(cfg.cpuMipPreparationEnabled);
		assertFalse(cfg.largeAtlasFixerEnabled);
		assertFalse(cfg.atlasMipParallelEnabled);
	}

	@Test
	void everyIndependentBooleanExposesItsPolicyDecision() {
		PackForgeConfig.Cfg cfg = new PackForgeConfig.Cfg();
		var plan = OptimizationPlan.create(cfg, EnumSet.allOf(PackForgeCapability.class), "mc26_1_to_26_2",
			new OptimizationCompatibility.State(false, false, false));
		var proxies = Set.of("reload_optimizer", "large_atlas_fixer", "atlas_retry_disable_with_iris", "startup_skip_smooth_boot");
		for (var option : PackForgeConfigScreenModel.allOptions()) {
			var decision = PackForgeConfigScreenModel.optimizationDecision(plan, option);
			if (!(option instanceof PackForgeConfigScreenModel.BooleanOption) || proxies.contains(option.id())) {
				assertNull(decision, option.id());
			} else {
				assertEquals(plan.decision(option.capability()), decision, option.id());
			}
		}
	}

	@Test
	void quickPackAndRrlsDisabledOptionsKeepRequestedValuesAndExplainOwnership() {
		PackForgeConfig.Cfg cfg = new PackForgeConfig.Cfg();
		for (var option : PackForgeConfigScreenModel.allOptions()) {
			if (option instanceof PackForgeConfigScreenModel.BooleanOption bool) bool.set(cfg, true);
		}
		var quickPack = OptimizationPlan.create(cfg, EnumSet.allOf(PackForgeCapability.class), "mc26_1_to_26_2",
			new OptimizationCompatibility.State(true, false, false));
		var missingPreviously = Set.of("atlas_retry", "font_reload_diagnostics", "atlas_phase_timings",
			"model_parse_timings", "startup_async_data", "startup_async_font_atlas", "startup_status_overlay");
		for (var option : PackForgeConfigScreenModel.allOptions()) {
			if (!missingPreviously.contains(option.id())) continue;
			var decision = PackForgeConfigScreenModel.optimizationDecision(quickPack, option);
			assertNotNull(decision, option.id());
			assertTrue(decision.requested(), option.id());
			assertFalse(decision.effective(), option.id());
			assertEquals(OptimizationPlan.Owner.QUICK_PACK, decision.owner(), option.id());
			assertTrue(decision.reason().contains("Quick Pack"), option.id());
		}
		var atlasCap = PackForgeConfigScreenModel.allOptions().stream()
			.filter(option -> option.id().equals("atlas_cap"))
			.findFirst()
			.orElseThrow();
		var atlasCapDecision = PackForgeConfigScreenModel.optimizationDecision(quickPack, atlasCap);
		assertNotNull(atlasCapDecision);
		assertTrue(atlasCapDecision.requested());
		assertFalse(atlasCapDecision.effective());
		assertEquals(OptimizationPlan.Owner.MINECRAFT, atlasCapDecision.owner());
		assertTrue(atlasCapDecision.reason().contains("Original texture dimensions are preserved"));
		var rrls = OptimizationPlan.create(cfg, EnumSet.allOf(PackForgeCapability.class), "mc26_1_to_26_2",
			new OptimizationCompatibility.State(true, true, false));
		for (var option : PackForgeConfigScreenModel.allOptions()) {
			if (!Set.of("loading_status_overlay", "loading_fade_out_disabled", "startup_status_overlay").contains(option.id())) continue;
			var decision = PackForgeConfigScreenModel.optimizationDecision(rrls, option);
			assertNotNull(decision, option.id());
			assertTrue(decision.requested(), option.id());
			assertFalse(decision.effective(), option.id());
			assertEquals(OptimizationPlan.Owner.RRLS, decision.owner(), option.id());
			assertTrue(decision.reason().contains("RRLS"), option.id());
		}
	}

	@Test
	void configCopyOwnsIndependentLists() {
		PackForgeConfig.Cfg source = new PackForgeConfig.Cfg();
		PackForgeConfig.Cfg copy = PackForgeConfig.copyOf(source);

		assertNotSame(source.atlasExcludeIds, copy.atlasExcludeIds);
		assertNotSame(source.atlasSplitTargets, copy.atlasSplitTargets);
		copy.atlasExcludeIds.add("example:test");
		assertFalse(source.atlasExcludeIds.contains("example:test"));
	}

	@Test
	void integerOptionsRejectOutOfRangeDraftValues() {
		PackForgeConfigScreenModel.IntegerOption option = (PackForgeConfigScreenModel.IntegerOption)
			PackForgeConfigScreenModel.allOptions().stream()
				.filter(candidate -> candidate.id().equals("atlas_retry_attempts"))
				.findFirst()
				.orElseThrow();

		assertThrows(IllegalArgumentException.class, () -> option.set(new PackForgeConfig.Cfg(), 0));
		assertThrows(IllegalArgumentException.class, () -> option.set(new PackForgeConfig.Cfg(), 11));
	}
}
