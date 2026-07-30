package com.teenkung.packforge.client.model;

import java.util.ArrayList;
import java.util.List;

/** Version-independent contiguous work partitioning for model parsers. */
public final class ModelBatchPlan {
	public static List<Range> create(int itemCount, int configuredBatchSize, boolean adaptive) {
		if (itemCount < 0) throw new IllegalArgumentException("itemCount must not be negative");
		if (itemCount == 0) return List.of();
		int size = Math.max(8, configuredBatchSize);
		if (adaptive) {
			if (itemCount >= 4096) size = Math.max(size, 128);
			else if (itemCount >= 2048) size = Math.max(size, 96);
			else if (itemCount <= 512) size = Math.min(size, 32);
		}
		List<Range> ranges = new ArrayList<>((itemCount + size - 1) / size);
		for (int from = 0; from < itemCount; from += size) {
			ranges.add(new Range(from, Math.min(itemCount, from + size)));
		}
		return List.copyOf(ranges);
	}

	public record Range(int fromInclusive, int toExclusive) {
		public Range {
			if (fromInclusive < 0 || toExclusive < fromInclusive) throw new IllegalArgumentException("invalid range");
		}
	}

	private ModelBatchPlan() {}
}
