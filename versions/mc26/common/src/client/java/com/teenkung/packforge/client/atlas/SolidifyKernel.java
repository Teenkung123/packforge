package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.teenkung.packforge.concurrent.PreparationBudget;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

/** Synchronous CPU-only solidify; false leaves pixels untouched for the caller's fallback. */
public final class SolidifyKernel {
	private static final long UNREACHED = Long.MAX_VALUE;
	private static final int RGB_MASK = 0x00ffffff;
	public static boolean solidify(NativeImage image, PreparationBudget.Scope budget) {
		if (image == null || budget == null || image.getClass() != NativeImage.class
			|| image.format() != NativeImage.Format.RGBA) return false;
		int width = image.getWidth(), height = image.getHeight();
		long pixels = (long) width * height;
		if (width <= 0 || height <= 0 || pixels > Integer.MAX_VALUE - 8) return false;
		int rankBits = Integer.SIZE - Integer.numberOfLeadingZeros((int) pixels - 1);
		int distanceBits = Integer.SIZE - Integer.numberOfLeadingZeros(width + height - 1);
		int distanceShift = rankBits + 24;
		if (distanceShift + distanceBits > 63) return false;
		PreparationBudget.Reservation allocation = budget.tryReserve(8L * pixels + 256L);
		if (allocation == null) return false;
		try (allocation) {
			apply(image, width, height, (int) pixels, 1L << distanceShift);
		}
		return true;
	}

	private static void apply(NativeImage image, int width, int height, int pixels, long step) {
		// NativeImage exposes this CPU buffer and uses the same LWJGL view for mappedCopy.
		// The view never escapes the caller's exclusive image lifetime or crosses a GPU boundary.
		long pointer = image.getPointer();
		if (pointer == 0) throw new IllegalStateException("Image is not allocated.");
		IntBuffer original = MemoryUtil.memIntBuffer(pointer, pixels);
		long[] nearest = new long[pixels];
		boolean hasSeed = false;
		boolean hasTransparent = false;
		for (int y = 0, position = 0; y < height; y++) {
			for (int x = 0; x < width; x++, position++) {
				int color = original.get(position);
				boolean seed = (color >>> 24) != 0;
				// Compare distance, then vanilla's column-first seed rank. RGB travels with
				// its unique seed, removing the output coordinate division and second array.
				nearest[position] = seed ? (((long) x * height + y) << 24) | (color & RGB_MASK) : UNREACHED;
				hasSeed |= seed;
				hasTransparent |= !seed;
			}
		}
		// Solidify changes only fully transparent pixels; all other pixels already
		// contain the exact required output, including partially transparent seeds.
		if (!hasTransparent) return;
		if (hasSeed && hasTransparent) {
			// Each shortest four-neighbor path can be split into a forward and backward
			// monotone path. Lexicographic (distance, seed) minima match vanilla FIFO ties.
			for (int y = 0, position = 0; y < height; y++) {
				for (int x = 0; x < width; x++, position++) {
					long value = nearest[position];
					if (value < step) continue;
					if (x > 0) value = nearer(value, nearest[position - 1], step);
					if (y > 0) value = nearer(value, nearest[position - width], step);
					nearest[position] = value;
				}
			}
			for (int y = height - 1, position = pixels - 1; y >= 0; y--) {
				for (int x = width - 1; x >= 0; x--, position--) {
					long value = nearest[position];
					if (value < step) continue;
					if (x + 1 < width) value = nearer(value, nearest[position + 1], step);
					if (y + 1 < height) value = nearer(value, nearest[position + width], step);
					nearest[position] = value;
				}
			}
		}
		for (int position = 0; position < pixels; position++) {
			long label = nearest[position];
			if (label < step) continue;
			original.put(position, label == UNREACHED ? 0 : (int) label & RGB_MASK);
		}
	}

	private static long nearer(long current, long neighbor, long step) {
		// Incrementing the sentinel would overflow and incorrectly win the minimum.
		return neighbor == UNREACHED ? current : Math.min(current, neighbor + step);
	}

	private SolidifyKernel() {}
}
