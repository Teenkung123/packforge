package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
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
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The data produced by the 1.20.1 provider warm-up pass that FontSet.reload
 * would otherwise derive a second time on the render thread.
 *
 * <p>This deliberately keeps provider ownership with Minecraft.  It contains
 * only provider references and the immutable width buckets needed to replay
 * vanilla's provider selection after FontSet has performed its normal reset
 * and special-glyph setup.</p>
 */
public final class RawFontPreparedSelection implements AutoCloseable {
	private List<GlyphProvider> activeProviders;
	private Int2ObjectMap<IntList> glyphsByWidth;
	private Runnable release;
	private int applications;
	private boolean retired;

	private RawFontPreparedSelection(List<GlyphProvider> activeProviders,
		Int2ObjectMap<IntList> glyphsByWidth, Runnable release) {
		this.activeProviders = List.copyOf(activeProviders);
		for (var entry : glyphsByWidth.int2ObjectEntrySet()) {
			((IntArrayList) entry.getValue()).trim();
			entry.setValue(IntLists.unmodifiable(entry.getValue()));
		}
		this.glyphsByWidth = Int2ObjectMaps.unmodifiable(glyphsByWidth);
		this.release = release;
	}

	static RawFontPreparedSelection compute(List<GlyphProvider> providers,
		PreparationBudget.Reservation allocation) {
		try {
			IntOpenHashSet supported = new IntOpenHashSet();
			for (GlyphProvider provider : providers) {
				supported.addAll((IntCollection) provider.getSupportedGlyphs());
			}
			Set<GlyphProvider> used = new HashSet<>();
			Int2ObjectOpenHashMap<IntList> widths = new Int2ObjectOpenHashMap<>();
			supported.forEach(codepoint -> {
				for (GlyphProvider provider : providers) {
					GlyphInfo glyph = provider.getGlyph(codepoint);
					if (glyph == null) continue;
					used.add(provider);
					if (glyph != SpecialGlyphs.MISSING) {
						widths.computeIfAbsent(Mth.ceil(glyph.getAdvance(false)), ignored -> new IntArrayList())
							.add(codepoint);
					}
					break;
				}
			});
			List<GlyphProvider> active = providers.stream().filter(used::contains).toList();
			long retained = retainedBytes(active.size(), widths);
			allocation.reduceTo(retained);
			return new RawFontPreparedSelection(active, widths, allocation::close);
		} catch (RuntimeException | Error failure) {
			allocation.close();
			throw failure;
		}
	}

	static long requestedBytes(int slots, long advertisedGlyphs) {
		// Covers the union set, provider/used sets, width lists and growth slack.
		// The cap is checked before touching a provider's glyph set.
		try {
			return Math.addExact(8192L + slots * 512L, Math.multiplyExact(advertisedGlyphs, 192L));
		} catch (ArithmeticException ignored) {
			return Long.MAX_VALUE;
		}
	}

	private static long retainedBytes(int activeProviders, Int2ObjectMap<IntList> widths) {
		long codepoints = 0L;
		for (IntList codes : widths.values()) codepoints += codes.size();
		return 4096L + activeProviders * 256L + widths.size() * 160L + codepoints * Integer.BYTES;
	}

	public List<GlyphProvider> activeProviders() {
		return activeProviders;
	}

	public Int2ObjectMap<IntList> glyphsByWidth() {
		return glyphsByWidth;
	}

	public boolean available() {
		return release != null && !retired;
	}

	/** Pins immutable selection data through Minecraft's complete reload lifecycle. */
	synchronized boolean acquire() {
		if (!available()) return false;
		applications++;
		return true;
	}

	synchronized void releaseApplication() {
		if (applications <= 0) throw new IllegalStateException("No active font selection application");
		applications--;
		if (applications == 0 && retired) releaseNow();
	}

	@Override
	public synchronized void close() {
		retired = true;
		if (applications == 0) releaseNow();
	}

	private void releaseNow() {
		Runnable action = release;
		release = null;
		activeProviders = List.of();
		glyphsByWidth = Int2ObjectMaps.emptyMap();
		if (action != null) action.run();
	}
}
