package com.teenkung.packforge.loader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private final Object statusLock = new Object();
	private final Map<String, String> listenerLabels = new LinkedHashMap<>();
	private final Map<String, Integer> pendingListeners = new LinkedHashMap<>();
	private final Map<String, Integer> preparing = new LinkedHashMap<>();
	private final Map<String, Integer> applying = new LinkedHashMap<>();
	private int completedListeners;
	private boolean workObserved;
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
		synchronized (statusLock) {
			if (!complete.compareAndSet(false, true)) return;
			active = false;
			phase = finalPhase;
			detail = finalDetail;
			activeListeners.set(0);
			activePrepareTasks.set(0);
			activeApplyTasks.set(0);
			listenerLabels.clear();
			pendingListeners.clear();
			preparing.clear();
			applying.clear();
		}
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
		return statusSnapshot().phase();
	}

	String detail() {
		return statusSnapshot().detail();
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

	String readableListener(String name) {
		synchronized (statusLock) {
			if (complete.get()) return "resources";
			return listenerLabels.computeIfAbsent(name, ReloadStatus::readableListener);
		}
	}

	void listenerStarted(String listenerName) {
		started(pendingListeners, activeListeners, listenerName);
	}

	void listenerFinished() {
		listenerFinished(null);
	}

	void listenerFinished(String listenerName) {
		synchronized (statusLock) {
			if (finished(pendingListeners, activeListeners, listenerName)) completedListeners++;
		}
	}

	void prepareStarted(String listenerName) {
		started(preparing, activePrepareTasks, listenerName);
	}

	void prepareFinished() {
		prepareFinished(null);
	}

	void prepareFinished(String listenerName) {
		finished(preparing, activePrepareTasks, listenerName);
	}

	void applyStarted(String listenerName) {
		started(applying, activeApplyTasks, listenerName);
	}

	void applyFinished() {
		applyFinished(null);
	}

	void applyFinished(String listenerName) {
		finished(applying, activeApplyTasks, listenerName);
	}

	private void started(Map<String, Integer> names, AtomicInteger count, String name) {
		synchronized (statusLock) {
			if (complete.get()) return;
			workObserved = true;
			names.merge(name, 1, Integer::sum);
			count.incrementAndGet();
		}
	}

	private boolean finished(Map<String, Integer> names, AtomicInteger count, String name) {
		synchronized (statusLock) {
			if (complete.get() || names.isEmpty()) return false;
			// Legacy no-argument callers finish the oldest matching phase; internal
			// task/future observers always supply their captured listener name.
			String key = name == null ? names.keySet().iterator().next() : name;
			Integer outstanding = names.get(key);
			if (outstanding == null) return false;
			if (outstanding == 1) names.remove(key);
			else names.put(key, outstanding - 1);
			decrement(count);
			return true;
		}
	}

	/** Bounded snapshot: at most three names, regardless of task count. */
	StatusSnapshot statusSnapshot() {
		synchronized (statusLock) {
			if (complete.get()) return new StatusSnapshot(phase, detail, 0, completedListeners, true);
			if (!applying.isEmpty()) return snapshot("Applying", applying, activeApplyTasks.get());
			if (!preparing.isEmpty()) return snapshot("Preparing", preparing, activePrepareTasks.get());
			if (!pendingListeners.isEmpty()) return snapshot("Loading", pendingListeners, activeListeners.get());
			return new StatusSnapshot(workObserved ? "Finishing" : "Starting", "resource reload", 0, completedListeners, false);
		}
	}

	private StatusSnapshot snapshot(String currentPhase, Map<String, Integer> names, int count) {
		StringBuilder text = new StringBuilder();
		int shown = 0;
		for (String name : names.keySet()) {
			if (shown == 3) break;
			if (shown++ != 0) text.append(", ");
			text.append(name);
		}
		if (names.size() > shown) text.append(" +").append(names.size() - shown).append(" more");
		return new StatusSnapshot(currentPhase, text.toString(), count, completedListeners, false);
	}

	record StatusSnapshot(String phase, String detail, int activeCount, int completedListeners, boolean complete) {}

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
