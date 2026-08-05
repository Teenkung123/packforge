package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public record FontPreparedSelection(
	List<GlyphProvider.Conditional> providers,
	List<GlyphProvider> activeProviders,
	Int2ObjectMap<IntList> glyphsByWidth,
	long elapsedNs
) {
	public FontPreparedSelection {
		providers = List.copyOf(providers);
		activeProviders = List.copyOf(activeProviders);
		Int2ObjectOpenHashMap<IntList> copiedWidths = new Int2ObjectOpenHashMap<>();
		for (Int2ObjectMap.Entry<IntList> entry : glyphsByWidth.int2ObjectEntrySet()) {
			copiedWidths.put(entry.getIntKey(), IntLists.unmodifiable(new IntArrayList(entry.getValue())));
		}
		glyphsByWidth = Int2ObjectMaps.unmodifiable(copiedWidths);
	}

	public static FontPreparedSelection compute(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
		long startNs = System.nanoTime();
		List<GlyphProvider> selected = new ArrayList<>();
		IntOpenHashSet supported = new IntOpenHashSet();
		for (GlyphProvider.Conditional conditional : providers) {
			if (conditional.filter().apply(options)) {
				selected.add(conditional.provider());
				supported.addAll(conditional.provider().getSupportedGlyphs());
			}
		}

		Set<GlyphProvider> used = Collections.newSetFromMap(new IdentityHashMap<>());
		Int2ObjectOpenHashMap<IntList> widths = new Int2ObjectOpenHashMap<>();
		supported.forEach(codepoint -> {
			for (GlyphProvider provider : selected) {
				GlyphInfo glyph = provider.getGlyph(codepoint);
				if (glyph == null) {
					continue;
				}
				used.add(provider);
				if (glyph != SpecialGlyphs.MISSING) {
					widths.computeIfAbsent(Mth.ceil(glyph.getAdvance(false)), ignored -> new IntArrayList()).add(codepoint);
				}
				break;
			}
		});
		return new FontPreparedSelection(
			List.copyOf(providers),
			selected.stream().filter(used::contains).toList(),
			widths,
			System.nanoTime() - startNs
		);
	}
}
