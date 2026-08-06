package com.teenkung.packforge.loader;

import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadExecutionContextTest {
	@BeforeEach
	void resetBefore() {
		ReloadStatus.resetForTesting();
	}

	@AfterEach
	void resetAfter() {
		ReloadStatus.resetForTesting();
	}

	@Test
	void snapshotCopiesMutableExclusionsAndBoundsWorkerBudget() {
		Set<String> exclusions = new HashSet<>();
		exclusions.add("minecraft:gui");
		ReloadFeatureSnapshot snapshot = snapshot(false, false, exclusions, 1000);
		exclusions.add("minecraft:items");

		assertEquals(Set.of("minecraft:gui"), snapshot.atlasExclusionIds());
		assertEquals(32, snapshot.workerBudget());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.atlasExclusionIds().add("minecraft:items"));
	}

	@Test
	void staleCompletionCannotFinishNewerContext() {
		ReloadExecutionContext older = ReloadExecutionContext.startForTesting(snapshot(false, true, Set.of(), 1));
		ReloadStatus.start(older);
		ReloadExecutionContext newer = ReloadExecutionContext.startForTesting(snapshot(false, false, Set.of(), 1));
		ReloadStatus.start(newer);

		ReloadStatus.finish(older, new IllegalStateException("stale"));

		assertTrue(ReloadExecutionContext.isCurrent(newer));
		assertTrue(ReloadStatus.isActive());
		assertFalse(ReloadStatus.consumeSummaryToast() != null);
	}

	@Test
	void nestedBindingsRestoreThePreviousContextAndGlobalFallback() {
		ReloadExecutionContext older = ReloadExecutionContext.startForTesting(snapshot(false, false, Set.of(), 1));
		ReloadExecutionContext newer = ReloadExecutionContext.startForTesting(snapshot(true, false, Set.of(), 1));

		assertSame(newer, ReloadExecutionContext.current());
		try (ReloadExecutionContext.Scope olderBinding = ReloadExecutionContext.bind(older)) {
			assertSame(older, ReloadExecutionContext.current());
			try (ReloadExecutionContext.Scope newerBinding = ReloadExecutionContext.bind(newer)) {
				assertSame(newer, ReloadExecutionContext.current());
			}
			assertSame(older, ReloadExecutionContext.current());
		}
		assertSame(newer, ReloadExecutionContext.current());
	}

	@Test
	void queuedListenerTasksKeepOlderSnapshotAndMetricsAfterNewerReloadStarts() {
		ReloadExecutionContext older = ReloadExecutionContext.startForTesting(snapshot(false, false, Set.of(), 1));
		AtomicReference<Runnable> queuedPrepare = new AtomicReference<>();
		AtomicReference<Runnable> queuedApply = new AtomicReference<>();
		Executor prepareDelegate = queuedPrepare::set;
		Executor applyDelegate = queuedApply::set;
		Executor prepare = ReloadListenerTelemetry.prepareExecutor(older, "older", prepareDelegate);
		Executor apply = ReloadListenerTelemetry.applyExecutor(older, "older", applyDelegate);
		AtomicReference<ReloadFeatureSnapshot> prepareSnapshot = new AtomicReference<>();
		AtomicReference<ReloadFeatureSnapshot> applySnapshot = new AtomicReference<>();

		prepare.execute(() -> {
			prepareSnapshot.set(ReloadExecutionContext.current().features());
			LoaderTimings.recordGetResource();
		});
		apply.execute(() -> {
			applySnapshot.set(ReloadExecutionContext.current().features());
			LoaderTimings.recordGetResource();
		});

		ReloadExecutionContext newer = ReloadExecutionContext.startForTesting(snapshot(true, false, Set.of(), 1));
		assertNotSame(older.features(), newer.features());

		queuedPrepare.get().run();
		queuedApply.get().run();

		assertSame(newer, ReloadExecutionContext.current());
		assertSame(older.features(), prepareSnapshot.get());
		assertSame(older.features(), applySnapshot.get());
		assertEquals(2L, older.metrics().counters().getResourceCalls());
		assertEquals(0L, newer.metrics().counters().getResourceCalls());
	}

	@Test
	void failureCleanupFinishesExactContext() {
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(snapshot(false, true, Set.of(), 1));
		ReloadStatus.start(context);

		ReloadLifecycle.finishReload(context, new IllegalStateException("expected"));

		assertFalse(ReloadStatus.isActive());
		assertTrue(ReloadStatus.isComplete());
		assertFalse(ReloadExecutionContext.isCurrent(context));
		assertTrue(ReloadStatus.consumeSummaryToast() != null);
	}

	@Test
	void normalPathOnlyAddsContextBindingAndKeepsFutureIdentity() {
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(snapshot(false, false, Set.of(), 1));
		ReloadStatus.start(context);
		Executor executor = command -> command.run();

		assertNotSame(executor, ReloadListenerTelemetry.prepareExecutor(context, "listener", executor));
		assertNotSame(executor, ReloadListenerTelemetry.applyExecutor(context, "listener", executor));
		CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

		assertSame(future, ReloadListenerTelemetry.observeListenerFuture(context, "listener", future, 0L));
	}

	@Test
	void normalPathTracksOneListenerBoundaryAndClosesItOnFailure() {
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(snapshot(false, true, Set.of(), 1));
		ReloadStatus.start(context);
		CompletableFuture<Void> future = new CompletableFuture<>();

		ReloadListenerTelemetry.observeListenerFuture(context, "FontManager", future, 0L);
		assertEquals(1, context.metrics().activeListeners());

		future.completeExceptionally(new IllegalStateException("expected"));

		assertEquals(0, context.metrics().activeListeners());
		assertFalse(ReloadStatus.isStatusTextReady());
	}

	@Test
	void detailedTimingWrapsTasksAndKeepsExceptionSemantics() {
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(snapshot(true, false, Set.of(), 1));
		ReloadStatus.start(context);
		AtomicInteger calls = new AtomicInteger();
		Executor executor = command -> command.run();
		Executor wrapped = ReloadListenerTelemetry.prepareExecutor(context, "FontManager", executor);

		assertNotSame(executor, wrapped);
		assertThrows(IllegalStateException.class, () -> wrapped.execute(() -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected");
		}));
		assertEquals(1, calls.get());
		assertEquals(1L, context.metrics().listenerSnapshots().get(0).prepareTasks());
	}

	@Test
	void readinessRequiresSuccessfulShaderAndFontBoundaries() {
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(snapshot(false, true, Set.of(), 1));
		ReloadStatus.start(context);

		ReloadListenerTelemetry.apply("Shader Loader", () -> {}, false, true).run();
		assertFalse(ReloadStatus.isStatusTextReady());
		ReloadListenerTelemetry.apply("FontManager", () -> {}, false, true).run();

		assertTrue(ReloadStatus.isStatusTextReady());
	}

	@Test
	void countersNeverBecomeNegativeAndStayWithTheirContext() {
		ReloadExecutionContext older = ReloadExecutionContext.startForTesting(snapshot(true, false, Set.of(), 1));
		ReloadStatus.start(older);
		ReloadStatus.prepareFinished(older);
		ReloadStatus.applyFinished(older);
		assertEquals(0, older.metrics().activePrepareTasks());
		assertEquals(0, older.metrics().activeApplyTasks());

		LoaderTimings.recordGetResource(older);
		ReloadExecutionContext newer = ReloadExecutionContext.startForTesting(snapshot(true, false, Set.of(), 1));
		ReloadStatus.start(newer);
		LoaderTimings.recordGetResource();

		assertEquals(1L, older.metrics().counters().getResourceCalls());
		assertEquals(1L, newer.metrics().counters().getResourceCalls());
	}

	private static ReloadFeatureSnapshot snapshot(boolean listenerTimings, boolean summary, Set<String> exclusions, int workers) {
		return new ReloadFeatureSnapshot(
			true, true, true, false, true, listenerTimings, true, true, false, summary,
			true, true, true, 64, false, false, false, false, true, false,
			false, false, 128, false, 128, true, 256, exclusions, false, 2,
			false, false, false, false, true, true, true, workers, 4, true,
			false, false, false, false, ReloadFeatureSnapshot.boundedWorkerBudget(workers, 1)
		);
	}
}
