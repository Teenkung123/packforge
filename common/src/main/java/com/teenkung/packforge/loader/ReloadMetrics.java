package com.teenkung.packforge.loader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Mutable counters owned by exactly one {@link ReloadExecutionContext}. */
public final class ReloadMetrics {
	private final long startNs = System.nanoTime();
	private final AtomicBoolean complete = new AtomicBoolean();
	private final AtomicInteger activeListeners = new AtomicInteger();
	private final AtomicInteger activePrepareTasks = new AtomicInteger();
	private final AtomicInteger activeApplyTasks = new AtomicInteger();
	private final LongAdder getResourceCalls = new LongAdder();
	private final LongAdder getNamespacesCalls = new LongAdder();
	private final LongAdder listResourcesCalls = new LongAdder();
	private final LongAdder fullScansAvoided = new LongAdder();
	private final ConcurrentHashMap<String, ListenerTiming> listenerTimings = new ConcurrentHashMap<>();
	private volatile boolean active;
	private volatile String phase = "Starting";
	private volatile String detail = "resource reload";

	void beginStatus() {
		if (complete.get()) {
			return;
		}
		active = true;
		phase = "Starting";
		detail = "resource reload";
	}

	void finishStatus(String finalPhase, String finalDetail) {
		if (!complete.compareAndSet(false, true)) {
			return;
		}
		active = false;
		phase = finalPhase;
		detail = finalDetail;
		activeListeners.set(0);
		activePrepareTasks.set(0);
		activeApplyTasks.set(0);
	}

	void finishStatus() {
		finishStatus("Finishing", "resource reload");
	}

	boolean isComplete() {
		return complete.get();
	}

	boolean isActive() {
		return active && !complete.get();
	}

	long elapsedNs() {
		return Math.max(0L, System.nanoTime() - startNs);
	}

	long startNs() {
		return startNs;
	}

	String phase() {
		return phase;
	}

	String detail() {
		return detail;
	}

	int activeListeners() {
		return activeListeners.get();
	}

	int activePrepareTasks() {
		return activePrepareTasks.get();
	}

	int activeApplyTasks() {
		return activeApplyTasks.get();
	}

	void listenerStarted(String listenerName) {
		if (complete.get()) {
			return;
		}
		activeListeners.incrementAndGet();
		phase = "Loading";
		detail = listenerName;
	}

	void listenerFinished() {
		decrement(activeListeners);
	}

	void prepareStarted(String listenerName) {
		if (complete.get()) {
			return;
		}
		activePrepareTasks.incrementAndGet();
		phase = "Preparing";
		detail = listenerName;
	}

	void prepareFinished() {
		decrement(activePrepareTasks);
	}

	void applyStarted(String listenerName) {
		if (complete.get()) {
			return;
		}
		activeApplyTasks.incrementAndGet();
		phase = "Applying";
		detail = listenerName;
	}

	void applyFinished() {
		decrement(activeApplyTasks);
	}

	void recordGetResource() {
		if (!complete.get()) {
			getResourceCalls.increment();
		}
	}

	void recordGetNamespaces() {
		if (!complete.get()) {
			getNamespacesCalls.increment();
			fullScansAvoided.increment();
		}
	}

	void recordListResources() {
		if (!complete.get()) {
			listResourcesCalls.increment();
			fullScansAvoided.increment();
		}
	}

	void recordListenerWall(String listenerName, long elapsedNs) {
		if (!complete.get()) {
			timing(listenerName).wallNs.add(nonNegative(elapsedNs));
		}
	}

	void recordListenerPrepare(String listenerName, long elapsedNs) {
		if (!complete.get()) {
			ListenerTiming timing = timing(listenerName);
			timing.prepareNs.add(nonNegative(elapsedNs));
			timing.prepareTasks.increment();
			timing.prepareMaxNs.accumulateAndGet(nonNegative(elapsedNs), Math::max);
		}
	}

	void recordListenerApply(String listenerName, long elapsedNs) {
		if (!complete.get()) {
			ListenerTiming timing = timing(listenerName);
			timing.applyNs.add(nonNegative(elapsedNs));
			timing.applyTasks.increment();
			timing.applyMaxNs.accumulateAndGet(nonNegative(elapsedNs), Math::max);
		}
	}

	CounterSnapshot counters() {
		return new CounterSnapshot(
			getResourceCalls.sum(),
			getNamespacesCalls.sum(),
			listResourcesCalls.sum(),
			fullScansAvoided.sum()
		);
	}

	List<ListenerSnapshot> listenerSnapshots() {
		List<ListenerSnapshot> result = new ArrayList<>();
		listenerTimings.forEach((name, timing) -> result.add(timing.snapshot(name)));
		result.sort(Comparator.comparingLong(ListenerSnapshot::activeNs).reversed());
		return List.copyOf(result);
	}

	private ListenerTiming timing(String listenerName) {
		return listenerTimings.computeIfAbsent(
			ReloadListenerTelemetry.canonicalName(listenerName),
			ignored -> new ListenerTiming()
		);
	}

	private static void decrement(AtomicInteger value) {
		value.updateAndGet(current -> Math.max(0, current - 1));
	}

	private static long nonNegative(long value) {
		return Math.max(0L, value);
	}

	public record CounterSnapshot(
		long getResourceCalls,
		long getNamespacesCalls,
		long listResourcesCalls,
		long fullScansAvoided
	) {
	}

	public record ListenerSnapshot(
		String name,
		long wallNs,
		long prepareNs,
		long prepareTasks,
		long prepareMaxNs,
		long applyNs,
		long applyTasks,
		long applyMaxNs
	) {
		long activeNs() {
			return prepareNs + applyNs;
		}
	}

	private static final class ListenerTiming {
		final LongAdder wallNs = new LongAdder();
		final LongAdder prepareNs = new LongAdder();
		final LongAdder prepareTasks = new LongAdder();
		final AtomicLong prepareMaxNs = new AtomicLong();
		final LongAdder applyNs = new LongAdder();
		final LongAdder applyTasks = new LongAdder();
		final AtomicLong applyMaxNs = new AtomicLong();

		ListenerSnapshot snapshot(String name) {
			return new ListenerSnapshot(
				name,
				wallNs.sum(),
				prepareNs.sum(),
				prepareTasks.sum(),
				prepareMaxNs.get(),
				applyNs.sum(),
				applyTasks.sum(),
				applyMaxNs.get()
			);
		}
	}
}
