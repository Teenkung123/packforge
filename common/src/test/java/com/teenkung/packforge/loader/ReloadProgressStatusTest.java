package com.teenkung.packforge.loader;

import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.config.OptimizationPlan;
import com.teenkung.packforge.config.PackForgeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadProgressStatusTest {
	@BeforeEach
	void reset() {
		ReloadStatus.resetForTesting();
	}

	@Test
	void completedLastStartedListenerCannotHideOutstandingFontWork() {
		ReloadExecutionContext context = start();
		CompletableFuture<Void> font = new CompletableFuture<>();
		CompletableFuture<Void> notifications = new CompletableFuture<>();
		ReloadListenerTelemetry.observeListenerFuture(context, "FontManager", font, 0L);
		ReloadListenerTelemetry.observeListenerFuture(context, "PeriodicNotifications", notifications, 0L);
		notifications.complete(null);
		assertEquals("Loading fonts", ReloadStatus.detailLine());
		assertTrue(ReloadStatus.line(1F).contains("1 stage complete"));
		assertFalse(ReloadStatus.line(1F).contains("100%"));
		font.complete(null);
		assertEquals("Finishing resource reload", ReloadStatus.detailLine());
	}

	@Test
	void repeatedListenerNamesRemainUntilEveryFutureCompletes() {
		ReloadExecutionContext context = start();
		CompletableFuture<Void> first = new CompletableFuture<>();
		CompletableFuture<Void> second = new CompletableFuture<>();
		ReloadListenerTelemetry.observeListenerFuture(context, "FontManager", first, 0L);
		ReloadListenerTelemetry.observeListenerFuture(context, "FontManager", second, 0L);
		first.complete(null);
		assertEquals("Loading fonts", ReloadStatus.detailLine());
		assertEquals(1, context.metrics().activeListeners());
		second.completeExceptionally(new IllegalStateException("expected"));
		assertEquals(0, context.metrics().activeListeners());
		assertFalse(ReloadStatus.detailLine().contains("fonts"));
	}

	@Test
	void statusOnlyExecutorsTrackNestedTasksAndCloseExactNamesOnError() {
		ReloadExecutionContext context = start();
		assertFalse(context.features().taskExecutorWrappingEnabled());
		Executor direct = Runnable::run;
		Executor fonts = ReloadListenerTelemetry.prepareExecutor(context, "FontManager", direct);
		Executor notifications = ReloadListenerTelemetry.prepareExecutor(context, "PeriodicNotifications", direct);
		fonts.execute(() -> {
			assertThrows(IllegalStateException.class, () -> notifications.execute(() -> {
				assertEquals(2, context.metrics().activePrepareTasks());
				throw new IllegalStateException("expected");
			}));
			assertEquals("Preparing fonts", ReloadStatus.detailLine());
			fonts.execute(() -> assertEquals(2, context.metrics().activePrepareTasks()));
			assertEquals(1, context.metrics().activePrepareTasks());
		});
		assertEquals(0, context.metrics().activePrepareTasks());
		assertTrue(context.metrics().listenerSnapshots().isEmpty());
	}

	@Test
	void oldFutureCompletionAndRetiredTasksCannotChangeNewReload() {
		ReloadExecutionContext older = start();
		CompletableFuture<Void> old = new CompletableFuture<>();
		ReloadListenerTelemetry.observeListenerFuture(older, "PeriodicNotifications", old, 0L);
		ReloadExecutionContext newer = start();
		CompletableFuture<Void> current = new CompletableFuture<>();
		ReloadListenerTelemetry.observeListenerFuture(newer, "FontManager", current, 0L);
		old.complete(null);
		ReloadStatus.finish(older, new IllegalStateException("retired"), false);
		ReloadStatus.prepareStarted(older, "PeriodicNotifications");
		assertEquals("Loading fonts", ReloadStatus.detailLine());
		assertEquals(0, older.metrics().activePrepareTasks());
		assertEquals(1, newer.metrics().activeListeners());
		ReloadStatus.finish(newer, new IllegalStateException("expected"), false);
		current.complete(null);
		assertEquals("Failed resource reload", ReloadStatus.detailLine());
		assertTrue(ReloadStatus.line(1F).contains("failed"));
	}

	@Test
	void snapshotShowsOnlyBoundedActiveNamesAndIgnoresUnmatchedCompletion() {
		ReloadExecutionContext context = start();
		for (int i = 0; i < 100; i++) context.metrics().listenerStarted("listener " + i);
		context.metrics().listenerFinished("not registered");
		assertEquals(100, context.metrics().activeListeners());
		assertEquals("listener 0, listener 1, listener 2 +97 more", context.metrics().statusSnapshot().detail());
		context.metrics().finishStatus();
		assertEquals(0, context.metrics().statusSnapshot().activeCount());
	}

	private ReloadExecutionContext start() {
		ReloadFeatureSnapshot features = new ReloadFeatureSnapshot(
			true, true, true, false, true, false, true, true, false, false,
			true, true, true, 64, false, false, false, false, true, false,
			false, false, 128, false, 128, true, 256, Set.of(), false, 2,
			false, false, false, false, true, false, true, 1, 4, true,
			false, false, false, false, 1, false, 128, OptimizationPlan.capture(PackForgeConfig.get())
		);
		ReloadExecutionContext context = ReloadExecutionContext.startForTesting(features);
		ReloadStatus.start(context);
		return context;
	}
}
