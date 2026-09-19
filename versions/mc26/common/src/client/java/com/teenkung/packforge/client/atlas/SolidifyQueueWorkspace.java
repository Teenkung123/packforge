package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.concurrent.PreparationBudget;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

import java.util.function.Supplier;

/** Accounts scratch storage until the synchronous vanilla operation has released its last reference. */
public final class SolidifyQueueWorkspace implements AutoCloseable {
	private static final ThreadLocal<SolidifyQueueWorkspace> CURRENT = new ThreadLocal<>();
	private final SolidifyQueueWorkspace previous;
	private final PreparationBudget.Reservation reservation;
	private final int pixelCount;
	private boolean queueCreated;
	private boolean closed;

	private SolidifyQueueWorkspace(PreparationBudget.Reservation reservation, int pixelCount) {
		this.previous = CURRENT.get();
		this.reservation = reservation;
		this.pixelCount = pixelCount;
		CURRENT.set(this);
	}

	/** A null reservation binds a bypass, including inside an older admitted invocation. */
	public static SolidifyQueueWorkspace open(PreparationBudget.Scope budget, int width, int height) {
		long pixels = (long) width * height;
		boolean valid = width > 0 && height > 0 && pixels <= Integer.MAX_VALUE - 8;
		PreparationBudget.Reservation reservation = valid && budget != null
			? budget.tryReserve(pixels * Integer.BYTES + 128L) : null;
		return new SolidifyQueueWorkspace(reservation, reservation == null ? 0 : (int) pixels);
	}

	/** The mixin substitutes only the queue constructor, leaving every pixel operation in Minecraft. */
	public static IntArrayFIFOQueue newQueue() {
		return newQueue(IntArrayFIFOQueue::new);
	}

	public static IntArrayFIFOQueue newQueue(Supplier<IntArrayFIFOQueue> original) {
		SolidifyQueueWorkspace workspace = CURRENT.get();
		if (workspace == null || workspace.reservation == null) return original.get();
		if (workspace.queueCreated) throw new IllegalStateException("Solidify constructed more than one queue");
		workspace.queueCreated = true;
		return new SolidifyQueue(workspace.pixelCount);
	}

	@Override public void close() {
		if (closed) return;
		closed = true;
		if (previous == null) CURRENT.remove();
		else CURRENT.set(previous);
		if (reservation != null) reservation.close();
	}
}
