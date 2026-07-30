package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeCapability;
import com.teenkung.packforge.config.PackForgeConfig;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
