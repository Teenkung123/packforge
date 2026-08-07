package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FontSelectionRegistryTest {
	private static final GlyphProvider PROVIDER_A = new EmptyProvider();
	private static final GlyphProvider PROVIDER_B = new EmptyProvider();
	private static final GlyphProvider.Conditional CONDITIONAL_A =
		new GlyphProvider.Conditional(PROVIDER_A, FontOption.Filter.ALWAYS_PASS);
	private static final GlyphProvider.Conditional CONDITIONAL_B =
		new GlyphProvider.Conditional(PROVIDER_B, FontOption.Filter.ALWAYS_PASS);

	@Test
	void duplicateStacksShareOneGroupAndProviderOrderRemainsSignificant() {
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		ResourceLocation reversed = id("reversed");
		Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets = new LinkedHashMap<>();
		fontSets.put(first, List.of(CONDITIONAL_A, CONDITIONAL_B));
		fontSets.put(second, List.of(CONDITIONAL_A, CONDITIONAL_B));
		fontSets.put(reversed, List.of(CONDITIONAL_B, CONDITIONAL_A));

		List<FontSelectionRegistry.StackGroup> groups = FontSelectionRegistry.groupFontSets(fontSets);

		assertEquals(2, groups.size());
		assertEquals(List.of(first, second), groups.get(0).ids());
		assertNotSame(groups.get(0).key(), groups.get(1).key());
	}

	@Test
	void optimizedApplyUsesDirectFontIdAndRejectsChangedOptions() {
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		Preparation preparation = new Preparation(Map.of(first, List.of(), second, List.of()));
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(fontFeatures());
		try {
			FontSelectionRegistry.prepareAsync(preparation, Set.of(), Runnable::run).join();
			FontSelectionRegistry.beginApply(preparation);

			FontSelectionRegistry.beginFontSet(first);
			FontPreparedSelection firstSelection = FontSelectionRegistry.currentSelection(Set.of());
			FontSelectionRegistry.endFontSet();
			FontSelectionRegistry.beginFontSet(second);
			FontPreparedSelection secondSelection = FontSelectionRegistry.currentSelection(Set.of());
			FontSelectionRegistry.endFontSet();

			assertNotNull(firstSelection);
			assertSame(firstSelection, secondSelection);
			FontSelectionRegistry.beginFontSet(first);
			assertNull(FontSelectionRegistry.currentSelection(Set.of(FontOption.UNIFORM)));
			FontSelectionRegistry.endFontSet();
		} finally {
			FontSelectionRegistry.resetForReload();
			ReloadExecutionContext.finish(context);
		}
	}

	@Test
	void repeatedResetClearsPreparedAndApplyingState() {
		ResourceLocation id = id("reset");
		Preparation preparation = new Preparation(Map.of(id, List.of()));
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(fontFeatures());
		try {
			FontSelectionRegistry.prepareAsync(preparation, Set.of(), Runnable::run).join();
			FontSelectionRegistry.resetForReload();
			FontSelectionRegistry.resetForReload();
			FontSelectionRegistry.beginApply(preparation);
			assertNull(FontSelectionRegistry.currentBundle());
		} finally {
			FontSelectionRegistry.resetForReload();
			ReloadExecutionContext.finish(context);
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("packforge_test", path);
	}

	private static ReloadFeatureSnapshot fontFeatures() {
		return new ReloadFeatureSnapshot(
			true, false, false, false, false, false, false, false, false, false,
			false, false, false, 1, false, false, false, false, true, false,
			false, false, 1, false, 1, false, 16, Set.of(), false, 1,
			false, false, false, false, false, false, false, 0, Thread.NORM_PRIORITY, true,
			false, false, false, false, 1
		);
	}

	private static final class Preparation implements FontManagerPreparationAccessor {
		private final Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets;

		private Preparation(Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets) {
			this.fontSets = fontSets;
		}

		@Override
		public Map<ResourceLocation, List<GlyphProvider.Conditional>> packforge$fontSets() {
			return fontSets;
		}
	}

	private static final class EmptyProvider implements GlyphProvider {
		@Override
		public IntSet getSupportedGlyphs() {
			return new IntOpenHashSet();
		}

		@Override
		public GlyphInfo getGlyph(int codepoint) {
			return null;
		}

		@Override
		public void close() {}
	}
}
