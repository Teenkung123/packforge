package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.concurrent.PreparationBudget;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FontPreparedSelection implements AutoCloseable {
	private List<GlyphProvider.Conditional> providers;
	private List<GlyphProvider> activeProviders;
	private Int2ObjectMap<IntList> glyphsByWidth;
	private final long elapsedNs;
	private Runnable release;

	FontPreparedSelection(List<GlyphProvider.Conditional> providers, FontSelectionSummary summary, Runnable release) {
		this.providers = List.copyOf(providers);
		this.activeProviders = summary.bindActive(providers);
		this.glyphsByWidth = summary.widths();
		this.elapsedNs = summary.elapsedNs();
		this.release = release;
	}

	/** Takes exclusive ownership of freshly computed buckets without duplicating their arrays. */
	private FontPreparedSelection(List<GlyphProvider.Conditional> providers, List<GlyphProvider> activeProviders,
		Int2ObjectMap<IntList> widths, long elapsedNs, Runnable release) {
		this.providers = List.copyOf(providers);
		this.activeProviders = List.copyOf(activeProviders);
		for (var entry : widths.int2ObjectEntrySet()) {
			((IntArrayList) entry.getValue()).trim();
			entry.setValue(IntLists.unmodifiable(entry.getValue()));
		}
		this.glyphsByWidth = Int2ObjectMaps.unmodifiable(widths);
		this.elapsedNs = elapsedNs;
		this.release = release;
	}

	/** Width lists have been transferred to FontSet before its owning bundle closes. */
	@Override public void close() {
		Runnable action;
		synchronized (this) {
			action = release;
			release = null;
			providers = List.of(); activeProviders = List.of(); glyphsByWidth = Int2ObjectMaps.emptyMap();
		}
		if (action != null) action.run();
	}

	public List<GlyphProvider.Conditional> providers() { return providers; }
	public List<GlyphProvider> activeProviders() { return activeProviders; }
	public Int2ObjectMap<IntList> glyphsByWidth() { return glyphsByWidth; }
	public long elapsedNs() { return elapsedNs; }

	/** Caller has admitted all provider counts and transfers an accounted transient lease. */
	static FontPreparedSelection compute(List<GlyphProvider.Conditional> providers, Set<FontOption> options,
		PreparationBudget.Reservation allocation) {
		Objects.requireNonNull(allocation, "Font selection requires admitted capacity");
		try {
			FontPreparedSelection selection = computeOwned(providers, options, allocation);
			allocation.reduceTo(2048L + providers.size() * 256L
				+ FontSelectionSummary.retainedWidthsBytes(selection.glyphsByWidth));
			return selection;
		} catch (RuntimeException | Error failure) {
			allocation.close();
			throw failure;
		}
	}

	private static FontPreparedSelection computeOwned(List<GlyphProvider.Conditional> providers, Set<FontOption> options,
		PreparationBudget.Reservation allocation) {
		long startNs = System.nanoTime();
		ArrayList<GlyphProvider> selectedProviders = new ArrayList<>();
		IntOpenHashSet supportedGlyphs = new IntOpenHashSet();
		for (GlyphProvider.Conditional conditionalProvider : providers) {
			if (!conditionalProvider.filter().apply(options)) continue;
			GlyphProvider provider = conditionalProvider.provider();
			selectedProviders.add(provider);
			supportedGlyphs.addAll((IntCollection) provider.getSupportedGlyphs());
		}

		Set<GlyphProvider> usedProviders = new HashSet<>();
		Int2ObjectOpenHashMap<IntList> glyphsByWidth = new Int2ObjectOpenHashMap<>();
		// Match FontSet.selectProviders: advertised codepoints form a union, but every
		// selected provider can supply them. Only a non-null glyph claims priority.
		supportedGlyphs.forEach(codepoint -> {
			for (GlyphProvider provider : selectedProviders) {
				var glyph = provider.getGlyph(codepoint);
				if (glyph == null) continue;
				usedProviders.add(provider);
				if (glyph.info() == SpecialGlyphs.MISSING) break;
				glyphsByWidth.computeIfAbsent(Mth.ceil(glyph.info().getAdvance(false)),
					ignored -> new IntArrayList()).add(codepoint);
				break;
			}
		});

		List<GlyphProvider> activeProviders = selectedProviders.stream().filter(usedProviders::contains).toList();
		return new FontPreparedSelection(providers, activeProviders, glyphsByWidth,
			System.nanoTime() - startNs, allocation::close);
	}
}
