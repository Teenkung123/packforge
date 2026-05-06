package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record FontPreparedSelection(
	List<GlyphProvider.Conditional> providers,
	List<GlyphProvider> activeProviders,
	Int2ObjectMap<IntList> glyphsByWidth,
	long elapsedNs
) {
	public static FontPreparedSelection compute(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
		long startNs = System.nanoTime();
		IntOpenHashSet supportedGlyphs = new IntOpenHashSet();
		ArrayList<GlyphProvider> selectedProviders = new ArrayList<>();
		for (GlyphProvider.Conditional conditionalProvider : providers) {
			if (!conditionalProvider.filter().apply(options)) continue;
			GlyphProvider provider = conditionalProvider.provider();
			selectedProviders.add(provider);
			supportedGlyphs.addAll((IntCollection)provider.getSupportedGlyphs());
		}

		HashSet<GlyphProvider> usedProviders = new HashSet<>();
		Int2ObjectOpenHashMap<IntList> glyphsByWidth = new Int2ObjectOpenHashMap<>();
		supportedGlyphs.forEach(codepoint -> {
			for (GlyphProvider provider : selectedProviders) {
				var glyph = provider.getGlyph(codepoint);
				if (glyph == null) continue;
				usedProviders.add(provider);
				if (glyph.info() == SpecialGlyphs.MISSING) break;
				glyphsByWidth.computeIfAbsent(Mth.ceil(glyph.info().getAdvance(false)), ignored -> new IntArrayList()).add(codepoint);
				break;
			}
		});

		List<GlyphProvider> activeProviders = selectedProviders.stream().filter(usedProviders::contains).toList();
		return new FontPreparedSelection(providers, activeProviders, glyphsByWidth, System.nanoTime() - startNs);
	}
}
