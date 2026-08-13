package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeCapability;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.config.QuickPackCompatibility;
import com.teenkung.packforge.compat.RuntimeMinecraftVersion;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeConfigScreenModelTest {
	private static final EnumSet<PackForgeCapability> QUICK_PACK_OWNED = EnumSet.of(
		PackForgeCapability.RESOURCE_PACK_INDEX,
		PackForgeCapability.ZIP_READ_POOL,
		PackForgeCapability.FONT_PROVIDER_PRESELECTION,
		PackForgeCapability.ATLAS_MIP_PARALLEL,
		PackForgeCapability.LOADING_FADE_CONTROL,
		PackForgeCapability.LOADING_STATUS_OVERLAY
	);
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
		assertEquals(List.of(PackForgeConfigScreenModel.Category.RELOAD, PackForgeConfigScreenModel.Category.DIAGNOSTICS),
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
		assertTrue(PackForgeConfigScreenModel.availableCategories(reload).containsAll(List.of(
			PackForgeConfigScreenModel.Category.RELOAD,
			PackForgeConfigScreenModel.Category.MODELS,
			PackForgeConfigScreenModel.Category.FONTS,
			PackForgeConfigScreenModel.Category.ATLAS,
			PackForgeConfigScreenModel.Category.COMPATIBILITY,
			PackForgeConfigScreenModel.Category.DIAGNOSTICS)));
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
		assertTrue(PackForgeConfigScreenModel.availableCategories(oldReload).contains(PackForgeConfigScreenModel.Category.RELOAD));
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

	@Test
	void schemaCarriesTypeDefaultAndValidationForEveryOption() {
		assertTrue(PackForgeConfigScreenModel.allOptions().stream()
			.allMatch(option -> !option.type().isBlank() && !option.defaultValue().isBlank() && !option.validation().isBlank()));
	}

	@Test
	void rejectsDuplicateOptionIds() {
		PackForgeConfigScreenModel.OptionSpec option = option("loader_index");

		assertThrows(IllegalStateException.class, () -> PackForgeConfigScreenModel.validateUniqueOptionIds(List.of(option, option)));
	}

	@Test
	void effectiveStateShowsQuickPackOwnershipWithoutChangingDraftValue() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		PackForgeConfigScreenModel.OptionSpec option = PackForgeConfigScreenModel.allOptions().stream()
			.filter(candidate -> candidate.id().equals("loader_index"))
			.findFirst()
			.orElseThrow();
		QuickPackCompatibility.Profile quickPack = new QuickPackCompatibility.Profile(
			QuickPackCompatibility.Status.MODULE_HANDOFF,
			Optional.of("1.5.7"),
			java.util.Set.of(PackForgeCapability.RESOURCE_PACK_INDEX)
		);

		PackForgeConfigScreenModel.EffectiveState state = PackForgeConfigScreenModel.effectiveState(option, config, quickPack);

		assertEquals("on", state.configuredValue());
		assertEquals("off", state.effectiveValue());
		assertFalse(state.effective());
		assertEquals("quick-pack", state.externalOwner());
		assertEquals("on", option.defaultValue());
		assertTrue(config.loaderIndexEnabled);
	}

	@Test
	void quickPackOwnsOnlyOverlapOptionsAndNotTheirMasterOrDiagnostics() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.reloadOptimizerEnabled = true;
		config.largeAtlasFixerEnabled = true;
		config.loaderZipPoolEnabled = true;
		config.loadingScreenFadeOutDisabled = true;
		config.atlasMipParallelEnabled = true;
		config.loaderTimingsEnabled = true;
		config.reloadSummaryToastEnabled = true;
		config.fontReloadDiagnosticsEnabled = true;
		config.fontBitmapProviderCacheEnabled = true;
		config.atlasDecodeBatchingEnabled = true;
		config.immediatelyFastFontAtlasCompatEnabled = true;
		config.modelParseBatchingEnabled = true;
		QuickPackCompatibility.Profile quickPack = new QuickPackCompatibility.Profile(
			QuickPackCompatibility.Status.MODULE_HANDOFF,
			Optional.of("1.5.7"),
			QUICK_PACK_OWNED
		);

		String previousRuntime = RuntimeMinecraftVersion.current();
		RuntimeMinecraftVersion.configure("26.2");
		try {
			for (String id : List.of(
				"loader_index",
				"loader_zip_pool",
				"loading_status_overlay",
				"loading_fade_out_disabled",
				"font_provider_selection",
				"atlas_mip_parallel",
				"atlas_mip_batch_size"
			)) {
				PackForgeConfigScreenModel.EffectiveState state = PackForgeConfigScreenModel.effectiveState(
					option(id),
					config,
					quickPack
				);
				assertFalse(state.effective(), id);
				assertEquals("quick-pack", state.externalOwner(), id);
				assertFalse(state.disabledReason().isBlank(), id);
			}

			for (String id : List.of(
				"reload_optimizer",
				"loader_timings",
				"reload_summary_toast",
				"font_reload_diagnostics",
				"font_bitmap_cache",
				"atlas_decode_batching",
				"immediatelyfast_font_guard",
				"model_parse_batching"
			)) {
				PackForgeConfigScreenModel.EffectiveState state = PackForgeConfigScreenModel.effectiveState(
					option(id),
					config,
					quickPack
				);
				assertTrue(state.effective(), id);
				assertEquals("", state.externalOwner(), id);
				assertEquals("", state.disabledReason(), id);
			}
		} finally {
			RuntimeMinecraftVersion.configure(previousRuntime);
		}
	}

	private static PackForgeConfigScreenModel.OptionSpec option(String id) {
		return PackForgeConfigScreenModel.allOptions().stream()
			.filter(candidate -> candidate.id().equals(id))
			.findFirst()
			.orElseThrow();
	}
}
