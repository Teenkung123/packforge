package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

import java.util.HashSet;
import java.util.List;

/** Takes exclusive ownership of exact-size buckets; the sole caller relinquishes them. */
record FontSelectionSummary(List<Integer> winningSlots, List<Integer> selectedSlots,
	Int2ObjectMap<IntList> widths, long elapsedNs) {
	FontSelectionSummary {
		winningSlots = List.copyOf(winningSlots);
		selectedSlots = List.copyOf(selectedSlots);
		for (var entry : widths.int2ObjectEntrySet()) {
			entry.setValue(IntLists.unmodifiable(entry.getValue()));
		}
		widths = Int2ObjectMaps.unmodifiable(widths);
	}

	List<GlyphProvider> bindActive(List<GlyphProvider.Conditional> providers) {
		HashSet<GlyphProvider> used = new HashSet<>();
		for (int slot : winningSlots) used.add(providers.get(slot).provider());
		return selectedSlots.stream().map(slot -> providers.get(slot).provider()).filter(used::contains).toList();
	}

	/** Conservative retained layout; byte lengths come from the final immutable arrays. */
	long retainedBytes() {
		// Includes references and conservatively counts every boxed slot as a distinct object.
		return retainedWidthsBytes(widths) + 32L * ((long) winningSlots.size() + selectedSlots.size());
	}

	static long retainedWidthsBytes(Int2ObjectMap<IntList> widths) {
		// Exact-sized maps use fastutil's 0.75 load factor; also cover its default minimum.
		long capacity = Math.max(32, HashCommon.arraySize(widths.size(), 0.75f));
		long bytes = 512L + aligned(24L + 4L * (capacity + 1)) + aligned(24L + 8L * (capacity + 1));
		for (IntList bucket : widths.values()) {
			// Immutable wrapper, IntArrayList, and its exclusively owned exact-length array.
			bytes += 112L + aligned(24L + 4L * bucket.size());
		}
		return bytes;
	}

	private static long aligned(long bytes) { return (bytes + 15L) & ~15L; }
}
