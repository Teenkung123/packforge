package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Identity-bearing, reload-scoped state shared by all work from one reload. */
public final class ReloadExecutionContext {
	private static final AtomicLong NEXT_TEST_ID = new AtomicLong();
	private static final AtomicReference<ReloadExecutionContext> CURRENT = new AtomicReference<>();
	private static final AtomicReference<ReloadExecutionContext> LAST_COMPLETED = new AtomicReference<>();
	private static final ThreadLocal<ReloadExecutionContext> BOUND = new ThreadLocal<>();
	private static final PreparationBudget PREPARATION_MEMORY = new PreparationBudget();

	private final long reloadId;
	private final ReloadFeatureSnapshot features;
	private final ReloadMetrics metrics;
	private final PreparationBudget.Scope preparationBudget;
	private ReloadReadCache readCache;
	private boolean preparationRetired;
	private boolean fontPreparationExpected;
	private int activeFontPreparations;

	private ReloadExecutionContext(long reloadId, ReloadFeatureSnapshot features) {
		this.reloadId = reloadId;
		this.features = features;
		this.metrics = new ReloadMetrics();
		this.preparationBudget = PREPARATION_MEMORY.openScope(features.optimizationMemoryMiB() * 1024L * 1024L);
		this.fontPreparationExpected = features.fontPrepareProviderSelectionEnabled();
	}

	public static ReloadExecutionContext start(long reloadId) {
		return start(reloadId, ReloadFeatureSnapshot.capture());
	}

	public static ReloadExecutionContext start(long reloadId, ReloadFeatureSnapshot features) {
		ReloadExecutionContext context = new ReloadExecutionContext(reloadId, features);
		LAST_COMPLETED.set(null);
		ReloadExecutionContext previous = CURRENT.getAndSet(context);
		if (previous != null) previous.retirePreparation();
		return context;
	}

	public static ReloadExecutionContext startForTesting(ReloadFeatureSnapshot features) {
		return start(NEXT_TEST_ID.incrementAndGet(), features);
	}

	public static ReloadExecutionContext current() {
		ReloadExecutionContext bound = BOUND.get();
		return bound == null ? CURRENT.get() : bound;
	}

	/**
	 * Binds one reload context to the current invocation or task thread.
	 *
	 * <p>The binding is deliberately separate from the global lifecycle pointer:
	 * a reload can outlive the thread that started it, and a newer reload can
	 * replace the global pointer while older queued work is still running.
	 * Always close the returned scope so nested bindings restore their exact
	 * previous context.</p>
	 */
	public static Scope bind(ReloadExecutionContext context) {
		return new Scope(Objects.requireNonNull(context, "context"), BOUND.get());
	}

	/**
	 * Creates the one lightweight runnable wrapper needed to carry a reload
	 * context across an executor boundary.  Normal reloads pay this binding
	 * cost but do not enable detailed telemetry or task timing.
	 */
	public static Runnable bindRunnable(ReloadExecutionContext context, Runnable command) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(command, "command");
		return () -> {
			try (Scope ignored = bind(context)) {
				command.run();
			}
		};
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
		boolean finished = CURRENT.compareAndSet(context, null);
		if (finished) {
			LAST_COMPLETED.set(context);
		}
		context.retirePreparation();
		return finished;
	}

	static void resetForTesting() {
		ReloadExecutionContext current = CURRENT.getAndSet(null);
		ReloadExecutionContext completed = LAST_COMPLETED.getAndSet(null);
		if (current != null) current.retirePreparation();
		if (completed != null) completed.retirePreparation();
		BOUND.remove();
	}

	/** Shared global accounting remains charged until outstanding owners release it. */
	public PreparationBudget.Scope preparationBudget() {
		return preparationBudget;
	}

	/** Give font planning first access to scratch memory; other work continues normally. */
	public synchronized boolean fontPreparationPending() {
		return fontPreparationExpected || activeFontPreparations > 0;
	}

	public synchronized void beginFontPreparation() {
		fontPreparationExpected = false;
		activeFontPreparations++;
	}

	public synchronized void endFontPreparation() {
		if (activeFontPreparations > 0) activeFontPreparations--;
	}

	/** Returns no cache when disabled or when this generation has retired. */
	public synchronized ReloadReadCache readCache() {
		if (preparationRetired || !features.resourceReadReuseEnabled() || preparationBudget.isRetired()) return null;
		if (readCache == null) readCache = new ReloadReadCache(preparationBudget, features.loaderTimingsEnabled());
		return readCache;
	}

	private void retirePreparation() {
		ReloadReadCache retiredCache;
		synchronized (this) {
			if (preparationRetired) return;
			preparationRetired = true;
			retiredCache = readCache;
			readCache = null;
		}
		ReloadReadCache.Statistics readStatistics = retiredCache != null && features.loaderTimingsEnabled()
			? retiredCache.statistics() : null;
		Throwable failure = null;
		try {
			preparationBudget.retire();
		} catch (RuntimeException | Error error) {
			failure = error;
		}
		if (retiredCache != null) {
			try {
				retiredCache.close();
			} catch (RuntimeException | Error error) {
				if (failure == null) failure = error;
				else if (failure != error) failure.addSuppressed(error);
			}
		}
		if (failure instanceof RuntimeException exception) throw exception;
		if (failure instanceof Error error) throw error;
		if (readStatistics != null) {
			PackForge.LOGGER.info("PackForge resource read reuse: reload={} statistics={}", reloadId, readStatistics);
		}
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

	/** Restores the exact task/invocation binding that was active before entry. */
	public static final class Scope implements AutoCloseable {
		private final ReloadExecutionContext previous;
		private boolean closed;

		private Scope(ReloadExecutionContext context, ReloadExecutionContext previous) {
			this.previous = previous;
			BOUND.set(context);
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (previous == null) {
				BOUND.remove();
			} else {
				BOUND.set(previous);
			}
		}
	}
}
