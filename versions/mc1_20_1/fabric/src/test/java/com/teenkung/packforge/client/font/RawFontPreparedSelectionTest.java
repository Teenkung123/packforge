package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.teenkung.packforge.concurrent.PreparationBudget;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawFontPreparedSelectionTest {
	@Test void preselectionMatchesVanillaRawProviderPriorityIncludingMissingGlyphs() {
		GlyphProvider missing = new MissingProvider(64);
		GlyphProvider first = new SpaceProvider(Map.of(65, 4f, 66, 5f));
		GlyphProvider lowerPriority = new SpaceProvider(Map.of(65, 9f, 67, 6f));
		List<GlyphProvider> stack = List.of(missing, first, lowerPriority);

		try (PreparationBudget.Scope scope = new PreparationBudget().openScope()) {
			RawFontPreparedSelection selection = RawFontPreparedSelection.compute(stack,
				scope.tryReserve(RawFontPreparedSelection.requestedBytes(stack.size(), 8)));
			try {
				VanillaSelection vanilla = vanillaSelect(stack);
				assertEquals(vanilla.active(), selection.activeProviders());
				assertEquals(vanilla.widths(), selection.glyphsByWidth());
				assertEquals(IntList.of(65), selection.glyphsByWidth().get(4));
				assertEquals(IntList.of(66), selection.glyphsByWidth().get(5));
				assertEquals(IntList.of(67), selection.glyphsByWidth().get(6));
			} finally {
				selection.close();
			}
			assertEquals(0L, scope.used());
		}
	}

	@Test void selectionIsRejectedBeforeScanningWhenTheSharedBudgetIsFull() {
		PreparationBudget budget = new PreparationBudget(4096);
		try (PreparationBudget.Scope scope = budget.openScope()) {
			GlyphProvider provider = new SpaceProvider(Map.of(65, 4f));
			assertTrue(RawFontPreparedSelection.requestedBytes(1, 1) > budget.limit());
			assertEquals(null, scope.tryReserve(RawFontPreparedSelection.requestedBytes(1, 1)));
			assertEquals(0L, budget.used());
			assertFalse(scope.isRetired());
		}
	}

	@Test void closingAPreparedSelectionReleasesItsRetainedLeaseAfterRetirement() {
		PreparationBudget budget = new PreparationBudget();
		PreparationBudget.Scope scope = budget.openScope();
		GlyphProvider provider = new SpaceProvider(Map.of(65, 4f, 66, 4f));
		RawFontPreparedSelection selection = RawFontPreparedSelection.compute(List.of(provider),
			scope.tryReserve(RawFontPreparedSelection.requestedBytes(1, 2)));
		assertNotNull(selection.glyphsByWidth().get(4));
		assertTrue(scope.used() > 0L);
		scope.retire();
		assertTrue(budget.used() > 0L, "retirement keeps a live selection charged");
		selection.close();
		assertEquals(0L, budget.used());
	}

	@Test void applicationPinKeepsSelectionStableUntilFontSetReloadReturns() {
		PreparationBudget budget = new PreparationBudget();
		PreparationBudget.Scope scope = budget.openScope();
		RawFontPreparedSelection selection = RawFontPreparedSelection.compute(
			List.of(new SpaceProvider(Map.of(65, 4f))),
			scope.tryReserve(RawFontPreparedSelection.requestedBytes(1, 1)));
		assertTrue(selection.acquire());
		selection.close();
		assertEquals(IntList.of(65), selection.glyphsByWidth().get(4));
		selection.releaseApplication();
		assertEquals(0L, budget.used());
	}

	private static VanillaSelection vanillaSelect(List<GlyphProvider> providers) {
		IntOpenHashSet advertised = new IntOpenHashSet();
		for (GlyphProvider provider : providers) advertised.addAll((IntCollection) provider.getSupportedGlyphs());
		Set<GlyphProvider> used = new HashSet<>();
		Int2ObjectMap<IntList> widths = new Int2ObjectOpenHashMap<>();
		advertised.forEach(codepoint -> {
			for (GlyphProvider provider : providers) {
				GlyphInfo glyph = provider.getGlyph(codepoint);
				if (glyph == null) continue;
				used.add(provider);
				if (glyph != SpecialGlyphs.MISSING) {
					widths.computeIfAbsent(Mth.ceil(glyph.getAdvance(false)), ignored -> new IntArrayList()).add(codepoint);
				}
				break;
			}
		});
		return new VanillaSelection(providers.stream().filter(used::contains).toList(), widths);
	}

	private record VanillaSelection(List<GlyphProvider> active, Int2ObjectMap<IntList> widths) {}

	private record MissingProvider(int codepoint) implements GlyphProvider {
		@Override public GlyphInfo getGlyph(int codepoint) {
			return codepoint == this.codepoint ? SpecialGlyphs.MISSING : null;
		}
		@Override public IntSet getSupportedGlyphs() { return IntSets.singleton(codepoint); }
	}
}
