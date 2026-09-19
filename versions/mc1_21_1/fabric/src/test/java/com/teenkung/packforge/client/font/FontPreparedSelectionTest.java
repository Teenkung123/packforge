package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.teenkung.packforge.concurrent.PreparationBudget;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontPreparedSelectionTest {
	private final PreparationBudget memory = new PreparationBudget();
	private final PreparationBudget.Scope scope = memory.openScope();
	private final List<FontPreparedSelection> owned = new ArrayList<>();

	@AfterEach void close() {
		owned.forEach(FontPreparedSelection::close);
		scope.retire();
		assertEquals(0, memory.used());
	}

	@Test
	void advertisedNullDoesNotClaimCodepoint() throws Exception {
		TestProvider empty = provider(new int[]{65}, Map.of());
		TestProvider fallback = provider(new int[]{65}, Map.of(65, glyph(4.25f)));
		FontPreparedSelection result = checkVanilla(List.of(always(empty), always(fallback)), Set.of());
		assertEquals(List.of(fallback), result.activeProviders());
		assertEquals(IntList.of(65), result.glyphsByWidth().get(5));
	}

	@Test
	void higherPriorityProviderCanSupplyCodepointAdvertisedByAnotherProvider() throws Exception {
		TestProvider first = provider(new int[]{66}, Map.of(65, glyph(2), 66, glyph(3)));
		TestProvider second = provider(new int[]{65}, Map.of(65, glyph(9)));
		FontPreparedSelection result = checkVanilla(List.of(always(first), always(second)), Set.of());
		assertEquals(List.of(first), result.activeProviders());
		assertEquals(IntList.of(65), result.glyphsByWidth().get(2));
	}

	@Test
	void missingGlyphClaimsPriorityButDoesNotEnterWidthBuckets() throws Exception {
		TestProvider missing = provider(new int[]{65}, Map.of(65, SpecialGlyphs.MISSING));
		TestProvider fallback = provider(new int[]{65}, Map.of(65, glyph(7)));
		FontPreparedSelection result = checkVanilla(List.of(always(missing), always(fallback)), Set.of());
		assertEquals(List.of(missing), result.activeProviders());
		assertTrue(result.glyphsByWidth().isEmpty());
	}

	@Test
	void optionFiltersAndProviderOrderMatchVanilla() throws Exception {
		TestProvider uniform = provider(new int[]{65}, Map.of(65, glyph(8)));
		TestProvider normal = provider(new int[]{65}, Map.of(65, glyph(4)));
		List<GlyphProvider.Conditional> providers = List.of(
			new GlyphProvider.Conditional(uniform, new FontOption.Filter(Map.of(FontOption.UNIFORM, true))),
			always(normal));
		assertEquals(List.of(normal), checkVanilla(providers, Set.of()).activeProviders());
		assertEquals(List.of(uniform), checkVanilla(providers, Set.of(FontOption.UNIFORM)).activeProviders());
	}

	@Test
	void spaceNegativeAndLargeAdvancesRetainVanillaBucketsAndOrder() throws Exception {
		TestProvider first = provider(new int[]{32, 65, 66, 67}, Map.of(
			32, glyph(4), 65, glyph(-2.25f), 66, glyph(40.5f), 67, glyph(4)));
		FontPreparedSelection result = checkVanilla(List.of(always(first)), Set.of());
		assertTrue(result.glyphsByWidth().get(4).contains(32));
		assertEquals(IntList.of(65), result.glyphsByWidth().get(-2));
		assertEquals(IntList.of(66), result.glyphsByWidth().get(41));
		assertThrows(UnsupportedOperationException.class, () -> result.glyphsByWidth().get(4).add(100));
	}

	@Test
	void overlappingProvidersPreserveExactVanillaWidthBucketOrder() throws Exception {
		TestProvider first = provider(new int[]{90, 32, 65}, Map.of(90, glyph(4), 32, glyph(4)));
		TestProvider second = provider(new int[]{65, 80, 90}, Map.of(65, glyph(4), 80, glyph(4), 90, glyph(8)));
		checkVanilla(List.of(always(first), always(second)), Set.of());
	}

	@Test
	void equalButDistinctProvidersKeepVanillaHashSetMembership() throws Exception {
		GlyphInfo shared = glyph(4);
		TestProvider first = provider(new int[]{65}, Map.of(65, shared));
		TestProvider second = provider(new int[]{65}, Map.of(65, shared));
		FontPreparedSelection result = checkVanilla(List.of(always(first), always(second)), Set.of());
		assertEquals(List.of(first, second), result.activeProviders());
	}

	@SuppressWarnings("unchecked")
	private FontPreparedSelection checkVanilla(List<GlyphProvider.Conditional> providers,
		Set<FontOption> options) throws Exception {
		FontSet vanilla = new FontSet(null, ResourceLocation.fromNamespaceAndPath("packforge_test", "font"));
		Method select = FontSet.class.getDeclaredMethod("selectProviders", List.class, Set.class);
		select.setAccessible(true);
		List<GlyphProvider> active = (List<GlyphProvider>) select.invoke(vanilla, providers, options);
		Field widths = FontSet.class.getDeclaredField("glyphsByWidth");
		widths.setAccessible(true);
		Int2ObjectMap<IntList> expected = (Int2ObjectMap<IntList>) widths.get(vanilla);
		long count = 0;
		for (GlyphProvider.Conditional provider : providers) count += provider.provider().getSupportedGlyphs().size();
		PreparationBudget.Reservation allocation = scope.tryReserve(8192L + providers.size() * 512L + count * 256L);
		FontPreparedSelection result = FontPreparedSelection.compute(providers, options, allocation);
		owned.add(result);
		assertEquals(active, result.activeProviders());
		assertEquals(expected, result.glyphsByWidth());
		return result;
	}

	private static GlyphProvider.Conditional always(GlyphProvider provider) {
		return new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS);
	}

	private static TestProvider provider(int[] advertised, Map<Integer, GlyphInfo> glyphs) {
		return new TestProvider(new IntOpenHashSet(advertised), glyphs);
	}

	private static GlyphInfo glyph(float advance) { return new SpaceProvider(Map.of(65, advance)).getGlyph(65); }

	private record TestProvider(IntSet advertised, Map<Integer, GlyphInfo> glyphs) implements GlyphProvider {
		@Override public IntSet getSupportedGlyphs() { return advertised; }
		@Override public GlyphInfo getGlyph(int codepoint) { return glyphs.get(codepoint); }
	}
}
