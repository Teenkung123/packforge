package com.teenkung.packforge.internal.compat;

import com.teenkung.packforge.platform.OptimizationCompatibility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinCompatibilityTest {
	private static final String MAIN = "com.teenkung.packforge.mixin.";
	private static final String CLIENT = "com.teenkung.packforge.client.mixin.";
	private static final OptimizationCompatibility.State STANDALONE = new OptimizationCompatibility.State(false, false, false);
	private static final OptimizationCompatibility.State QUICK_PACK = new OptimizationCompatibility.State(true, false, false);
	private static final OptimizationCompatibility.State RRLS = new OptimizationCompatibility.State(false, true, false);
	private static final List<String> WITHDRAWN = List.of(
		CLIENT + "font.BitmapProviderDefinitionMixin",
		CLIENT + "font.BitmapProviderImageAccessor"
	);
	private static final List<String> OVERLAPPING = List.of(
		MAIN + "loader.FilePackResourcesMixin",
		MAIN + "loader.ZipIoSupplierReadReuseMixin",
		MAIN + "loader.SharedZipFileAccessAccessor",
		MAIN + "loader.SharedZipFileAccessMixin",
		MAIN + "startup.UtilExecutorMixin",
		CLIENT + "atlas.SpriteLoaderMixin",
		CLIENT + "atlas.SpriteResourceLoaderMixin",
		CLIENT + "atlas.SpriteContentsMixin",
		CLIENT + "atlas.TextureAtlasMixin",
		CLIENT + "atlas.TextureUtilMixin",
		CLIENT + "atlas.ParallelMipMixin",
		CLIENT + "font.FontManagerPreparationAccessor",
		CLIENT + "font.FontManagerMixin",
		CLIENT + "font.FontProviderGlyphMapAccessor",
		CLIENT + "font.FontSetMixin",
		CLIENT + "font.SpaceProviderDefinitionCodecMixin",
		CLIENT + "model.ModelManagerMixin"
	);

	@Test
	void withdrawnBitmapRetentionNeverTransformsProvidersRegardlessOfOtherMods() {
		for (String mixin : WITHDRAWN) {
			for (var compatibility : List.of(STANDALONE, QUICK_PACK, RRLS,
				new OptimizationCompatibility.State(true, true, true))) {
				assertFalse(MixinCompatibility.shouldApply(mixin, compatibility), mixin);
			}
		}
	}

	@Test
	void quickPackOmitsCompleteOptimizationGroupsBeforeTransformation() {
		for (String mixin : OVERLAPPING) {
			assertFalse(MixinCompatibility.shouldApply(mixin, QUICK_PACK), mixin);
		}
	}

	@Test
	void standaloneAndRrlsKeepOptimizationHooksAvailableForRuntimeAdmission() {
		for (String mixin : OVERLAPPING) {
			assertTrue(MixinCompatibility.shouldApply(mixin, STANDALONE), mixin);
			assertTrue(MixinCompatibility.shouldApply(mixin, RRLS), mixin);
		}
	}

	@Test
	void eitherOverlayOwnerOmitsAllPackForgeOverlayAndFadeHooks() {
		String overlay = CLIENT + "ui.LoadingOverlayMixin";
		assertTrue(MixinCompatibility.shouldApply(overlay, STANDALONE));
		assertFalse(MixinCompatibility.shouldApply(overlay, QUICK_PACK));
		assertFalse(MixinCompatibility.shouldApply(overlay, RRLS));
		assertFalse(MixinCompatibility.shouldApply(overlay, new OptimizationCompatibility.State(true, true, true)));
	}

	@Test
	void retainsLifecycleAndConfigurationBridgesRequiredForSafeOperation() {
		for (String mixin : List.of(
			MAIN + "observe.ReloadableResourceManagerMixin",
			MAIN + "observe.SimpleReloadInstanceMixin",
			CLIENT + "compat.MinecraftGui26_1Mixin",
			CLIENT + "compat.Gui26_2Mixin",
			CLIENT + "config.PackSelectionScreenMixin",
			CLIENT + "model.FaceBakeryMixin",
			CLIENT + "options.OptionsMixin"
		)) {
			assertTrue(MixinCompatibility.shouldApply(mixin, QUICK_PACK), mixin);
			assertTrue(MixinCompatibility.shouldApply(mixin, RRLS), mixin);
		}
	}

	@Test
	void doesNotClaimOtherModsWithSimilarMixinNames() {
		assertTrue(MixinCompatibility.shouldApply("other.mod.mixin.font.FontManagerMixin", QUICK_PACK));
		assertTrue(MixinCompatibility.shouldApply(CLIENT + "fonts.FontManagerMixin", QUICK_PACK));
	}
}
