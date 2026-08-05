package com.teenkung.packforge.loader;

import com.teenkung.packforge.config.ReloadFeatureSnapshot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Identity-bearing, reload-scoped state shared by all work from one reload. */
public final class ReloadExecutionContext {
	private static final AtomicLong NEXT_TEST_ID = new AtomicLong();
	private static final AtomicReference<ReloadExecutionContext> CURRENT = new AtomicReference<>();
	private static final AtomicReference<ReloadExecutionContext> LAST_COMPLETED = new AtomicReference<>();

	private final long reloadId;
	private final ReloadFeatureSnapshot features;
	private final ReloadMetrics metrics;

	private ReloadExecutionContext(long reloadId, ReloadFeatureSnapshot features) {
		this.reloadId = reloadId;
		this.features = features;
		this.metrics = new ReloadMetrics();
	}

	public static ReloadExecutionContext start(long reloadId) {
		return start(reloadId, ReloadFeatureSnapshot.capture());
	}

	public static ReloadExecutionContext start(long reloadId, ReloadFeatureSnapshot features) {
		ReloadExecutionContext context = new ReloadExecutionContext(reloadId, features);
		LAST_COMPLETED.set(null);
		CURRENT.set(context);
		return context;
	}

	public static ReloadExecutionContext startForTesting(ReloadFeatureSnapshot features) {
		return start(NEXT_TEST_ID.incrementAndGet(), features);
	}

	public static ReloadExecutionContext current() {
		return CURRENT.get();
	}

	static ReloadExecutionContext visible() {
		ReloadExecutionContext current = CURRENT.get();
		return current == null ? LAST_COMPLETED.get() : current;
	}

	public static boolean isCurrent(ReloadExecutionContext context) {
		return context != null && CURRENT.get() == context;
	}

	/**
	 * Finishes only this exact context.  A completion from an older reload
	 * cannot clear a newer context because the compare-and-set is identity based.
	 */
	public static boolean finish(ReloadExecutionContext context) {
		if (context == null) {
			return false;
		}
		context.metrics.finishStatus();
		if (CURRENT.compareAndSet(context, null)) {
			LAST_COMPLETED.set(context);
			return true;
		}
		return false;
	}

	static void resetForTesting() {
		CURRENT.set(null);
		LAST_COMPLETED.set(null);
	}

	public long reloadId() {
		return reloadId;
	}

	public ReloadFeatureSnapshot features() {
		return features;
	}

	public ReloadMetrics metrics() {
		return metrics;
	}
}
