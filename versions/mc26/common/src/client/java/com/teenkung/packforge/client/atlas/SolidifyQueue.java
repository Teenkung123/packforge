package com.teenkung.packforge.client.atlas;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

import java.util.NoSuchElementException;

/** Single-pass FIFO for vanilla solidify, whose breadth-first traversal enqueues each pixel at most once. */
public final class SolidifyQueue extends IntArrayFIFOQueue {
	public SolidifyQueue(int pixelCount) {
		// Fastutil adds one ring-buffer sentinel; this queue uses all allocated slots.
		super(pixelCount - 1);
	}

	@Override public void enqueue(int value) {
		if (end == array.length) throw new IllegalStateException("Solidify enqueued more than one entry per pixel");
		array[end++] = value;
	}

	@Override public int dequeueInt() {
		if (isEmpty()) throw new NoSuchElementException();
		return array[start++];
	}

	@Override public boolean isEmpty() { return start == end; }
	@Override public int size() { return end - start; }
	@Override public int capacity() { return array.length; }
	@Override public void clear() { start = end = 0; }
	@Override public void trim() { }

	/** This narrow queue deliberately supports only the operations used by solidify. */
	@Override public void enqueueFirst(int value) { throw new UnsupportedOperationException("Solidify requires FIFO order"); }
	@Override public int dequeueLastInt() { throw new UnsupportedOperationException("Solidify requires FIFO order"); }

	int[] storageForTesting() { return array; }
}
