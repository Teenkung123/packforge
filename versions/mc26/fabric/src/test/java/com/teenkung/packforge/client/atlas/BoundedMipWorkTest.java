package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.concurrent.PreparationBudget;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BoundedMipWorkTest {
    private static final List<Integer> INPUT = IntStream.range(0, 64).boxed().toList();

    @Test
    void directExecutorCallsTheOriginalConsumerExactlyOnceAndPreservesItsCapturedMipLevel() {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        List<Integer> seen = new ArrayList<>();
        int resolvedMipLevel = 0; // Original consumer captures Minecraft's lowered level, not the requested 2.
        BoundedMipWork<Integer> work = prepare(scope, Runnable::run, value -> seen.add(value + resolvedMipLevel));
        assertNotNull(work);
        work.run();
        assertEquals(INPUT, seen);
        assertTrue(work.future().isDone());
        assertTrue(work.drained().isDone());
        assertEquals(64, work.worked());
        scope.retire();
        assertEquals(0, budget.used());
    }

    @Test
    void callbacksThatNeverStartAreDisarmedWithoutDelayingReadiness() {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        HoldingExecutor executor = new HoldingExecutor();
        AtomicInteger calls = new AtomicInteger();
        BoundedMipWork<Integer> work = prepare(scope, executor, value -> calls.incrementAndGet());
        work.run();
        assertTrue(work.future().isDone());
        assertTrue(work.drained().isDone());
        assertEquals(3, executor.queued.size());
        assertEquals(64, calls.get());
        scope.retire();
        assertEquals(0, budget.used());
        executor.runAll(); // Stale callbacks cannot access owners, reacquire memory, or invoke pixels.
        assertEquals(64, calls.get());
        assertEquals(0, budget.used());
    }

    @Test
    void originalWorkerOnSingleThreadExecutorNeverWaitsForItsQueuedChildren() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            PreparationBudget budget = new PreparationBudget();
            PreparationBudget.Scope scope = budget.openScope();
            try (var executor = Executors.newSingleThreadExecutor()) {
                AtomicInteger calls = new AtomicInteger();
                executor.submit(() -> {
                    BoundedMipWork<Integer> work = prepare(scope, executor, value -> calls.incrementAndGet());
                    work.run();
                    assertTrue(work.future().isDone());
                    work.future().join();
                }).get(5, TimeUnit.SECONDS);
                assertEquals(64, calls.get());
            }
            scope.retire();
            assertEquals(0, budget.used());
        });
    }

    @Test
    void twoAtlasParentsCanSaturateTheExecutorWithoutStarvingReadiness() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            PreparationBudget budget = new PreparationBudget();
            PreparationBudget.Scope scope = budget.openScope();
            CountDownLatch parents = new CountDownLatch(2);
            CountDownLatch baselines = new CountDownLatch(2);
            AtomicInteger calls = new AtomicInteger();
            try (var executor = Executors.newFixedThreadPool(2)) {
                Runnable atlas = () -> {
                    parents.countDown();
                    await(parents);
                    BoundedMipWork<Integer> work = prepare(scope, executor, value -> calls.incrementAndGet());
                    work.run();
                    baselines.countDown();
                    await(baselines); // Keep both executor slots occupied until both parents drained.
                    assertTrue(work.future().isDone());
                    work.future().join();
                };
                var first = executor.submit(atlas);
                var second = executor.submit(atlas);
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            }
            assertEquals(128, calls.get());
            scope.retire();
            assertEquals(0, budget.used());
        });
    }

    @Test
    void readinessWaitsOnlyForRunningWorkerAndCancellationKeepsItsScratchCharged() throws Exception {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FirstRunningExecutor executor = new FirstRunningExecutor(entered);
        AtomicInteger calls = new AtomicInteger();
        BoundedMipWork<Integer> work = prepare(scope, executor, value -> {
            if (value == 0) { entered.countDown(); await(release); }
            calls.incrementAndGet();
        });
        try {
            work.run();
            assertFalse(work.future().isDone());
            assertEquals(63, calls.get());
            assertTrue(work.future().cancel(false));
            assertTrue(work.future().isCancelled());
            assertFalse(work.drained().isDone());
            scope.retire();
            assertTrue(budget.used() >= 1024 * 1024, "Running image scratch must stay charged after cancellation/retirement");
        } finally { release.countDown(); executor.join(); }
        assertTrue(work.drained().isDone());
        assertEquals(64, calls.get());
        assertEquals(0, budget.used());
        executor.queued.runAll();
        assertEquals(64, calls.get());
    }

    @Test
    void originalFailureIsReportedAfterRunningWorkDrainsAndEarlierSlotWins() throws Exception {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FirstRunningExecutor executor = new FirstRunningExecutor(entered);
        IllegalStateException first = new IllegalStateException("original sprite zero context");
        IllegalArgumentException second = new IllegalArgumentException("original sprite one context");
        BoundedMipWork<Integer> work = prepare(scope, executor, value -> {
            if (value == 0) { entered.countDown(); await(release); throw first; }
            if (value == 1) throw second;
            fail("No later slot should start after failure");
        });
        try {
            work.run();
            assertFalse(work.future().isDone());
        } finally { release.countDown(); executor.join(); }
        CompletionException failure = assertThrows(CompletionException.class, work.future()::join);
        assertSame(first, failure.getCause());
        assertTrue(List.of(first.getSuppressed()).contains(second));
        assertTrue(work.drained().isDone());
        scope.retire();
        assertEquals(0, budget.used());
    }

    @Test
    void rejectionBeforeOrAfterAnAcceptedCallbackLeavesUnclaimedWorkToBaseline() {
        for (int accepted : new int[]{0, 1}) {
            PreparationBudget budget = new PreparationBudget();
            PreparationBudget.Scope scope = budget.openScope();
            HoldingExecutor waiting = new HoldingExecutor();
            RejectedExecutionException expected = new RejectedExecutionException("original executor rejection");
            AtomicInteger submits = new AtomicInteger(), calls = new AtomicInteger();
            Executor executor = command -> {
                if (submits.getAndIncrement() == accepted) throw expected;
                waiting.execute(command);
            };
            BoundedMipWork<Integer> work = prepare(scope, executor, value -> calls.incrementAndGet());
            work.run();
            work.future().join();
            assertTrue(work.drained().isDone());
            assertEquals(64, calls.get());
            scope.retire();
            assertEquals(0, budget.used());
            waiting.runAll();
            assertEquals(64, calls.get());
        }
    }

    @Test
    void rejectionAfterOneChildIsRunningFinishesEverySlotExactlyOnce() throws Exception {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FirstRunningExecutor running = new FirstRunningExecutor(entered);
        AtomicInteger submissions = new AtomicInteger();
        AtomicIntegerArray calls = new AtomicIntegerArray(64);
        Executor executor = command -> {
            if (submissions.getAndIncrement() == 0) running.execute(command);
            else throw new RejectedExecutionException("optional child queue is full");
        };
        BoundedMipWork<Integer> work = prepare(scope, executor, value -> {
            if (value == 0) { entered.countDown(); await(release); }
            calls.incrementAndGet(value);
        });
        try {
            work.run();
            assertFalse(work.future().isDone());
            for (int value = 1; value < 64; value++) assertEquals(1, calls.get(value));
        } finally { release.countDown(); running.join(); }
        work.future().join();
        for (int value = 0; value < 64; value++) assertEquals(1, calls.get(value));
        scope.retire();
        assertEquals(0, budget.used());
    }

    @Test
    void fontPriorityAndScratchDenialKeepBaselineProcessingEveryInput() {
        for (boolean fonts : new boolean[]{false, true}) {
            long metadata = 4096L + 192L * INPUT.size();
            PreparationBudget budget = new PreparationBudget(metadata + 512);
            PreparationBudget.Scope scope = budget.openScope();
            AtomicBoolean fontPending = new AtomicBoolean();
            AtomicInteger calls = new AtomicInteger();
            BoundedMipWork<Integer> work = BoundedMipWork.prepare(INPUT, Runnable::run, 8, scope,
                fontPending::get, values -> true, value -> 1024L * 1024, value -> calls.incrementAndGet(), Runnable::run);
            assertNotNull(work);
            fontPending.set(fonts);
            work.run();
            work.future().join();
            assertEquals(64, calls.get());
            assertEquals(metadata + 512, budget.peak());
            scope.retire();
            assertEquals(0, budget.used());
        }
    }

    @Test
    void unadmittedOwnershipOrInitialFontPriorityDoesNotStartAnyWork() {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        AtomicInteger calls = new AtomicInteger();
        assertNull(BoundedMipWork.prepare(INPUT, Runnable::run, 8, scope, () -> true,
            values -> true, value -> 1, value -> calls.incrementAndGet(), Runnable::run));
        assertEquals(0, budget.peak());
        assertNull(BoundedMipWork.prepare(INPUT, Runnable::run, 8, scope, () -> false,
            values -> false, value -> 1, value -> calls.incrementAndGet(), Runnable::run));
        assertEquals(0, budget.used());
        assertEquals(0, calls.get());
        scope.retire();
    }

    @Test
    void sharedReloadPermitsAreBoundedAndCapacityRejectionRemainsRetryable() {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        HoldingExecutor executor = new HoldingExecutor();
        BoundedMipWork<Integer> first = prepare(scope, executor, value -> {});
        BoundedMipWork<Integer> second = prepare(scope, executor, value -> {});
        BoundedMipWork<Integer> third = prepare(scope, executor, value -> {});
        assertNotNull(first); assertNotNull(second); assertNotNull(third);
        assertNull(prepare(scope, executor, value -> {})); // Seven extra lanes, across all atlases.
        first.run(); second.run(); third.run();
        assertEquals(7, executor.queued.size());
        BoundedMipWork<Integer> afterRelease = prepare(scope, executor, value -> {});
        assertNotNull(afterRelease);
        afterRelease.run();
        scope.retire();
        assertEquals(0, budget.used());
        executor.runAll();
    }

    @Test
    void explicitCancellationBeforeRunCannotTouchImages() {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        HoldingExecutor executor = new HoldingExecutor();
        AtomicInteger calls = new AtomicInteger();
        BoundedMipWork<Integer> work = prepare(scope, executor, value -> calls.incrementAndGet());
        assertTrue(work.future().cancel(false));
        work.run();
        assertTrue(work.future().isCancelled());
        assertTrue(work.drained().isDone());
        assertEquals(0, calls.get());
        scope.retire();
        assertEquals(0, budget.used());
        executor.runAll();
        assertEquals(0, calls.get());
    }

    @Test
    void retiredOptimizationScopeBeforeRunDoesNotCancelRequiredVanillaWork() {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        HoldingExecutor executor = new HoldingExecutor();
        AtomicInteger calls = new AtomicInteger();
        BoundedMipWork<Integer> work = prepare(scope, executor, value -> calls.incrementAndGet());
        scope.retire();
        work.run();
        work.future().join();
        assertFalse(work.future().isCancelled());
        assertTrue(work.drained().isDone());
        assertEquals(64, calls.get());
        assertEquals(0, budget.used());
        executor.runAll();
        assertEquals(64, calls.get());
    }

    @Test
    void retirementWhileOneChildRunsLetsBaselineDrainAndKeepsRunningScratchCharged() throws Exception {
        PreparationBudget budget = new PreparationBudget();
        PreparationBudget.Scope scope = budget.openScope();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FirstRunningExecutor executor = new FirstRunningExecutor(entered);
        AtomicIntegerArray calls = new AtomicIntegerArray(64);
        BoundedMipWork<Integer> work = prepare(scope, executor, value -> {
            if (value == 0) { entered.countDown(); await(release); }
            if (value == 1) scope.retire();
            calls.incrementAndGet(value);
        });
        try {
            work.run();
            assertFalse(work.future().isDone());
            for (int value = 1; value < 64; value++) assertEquals(1, calls.get(value));
            assertTrue(budget.used() >= 1024 * 1024);
        } finally { release.countDown(); executor.join(); }
        work.future().join();
        for (int value = 0; value < 64; value++) assertEquals(1, calls.get(value));
        assertEquals(0, budget.used());
    }

    private static BoundedMipWork<Integer> prepare(PreparationBudget.Scope scope, Executor executor, Consumer<Integer> action) {
        return BoundedMipWork.prepare(INPUT, executor, 8, scope, () -> false, values -> true,
            value -> 1024L * 1024, action, Runnable::run);
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "Worker rendezvous timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    private static final class HoldingExecutor implements Executor {
        final List<Runnable> queued = new ArrayList<>();
        @Override public void execute(Runnable command) { queued.add(command); }
        void runAll() { for (Runnable command : List.copyOf(queued)) command.run(); }
    }

    private static final class FirstRunningExecutor implements Executor {
        final HoldingExecutor queued = new HoldingExecutor();
        final CountDownLatch entered;
        Thread first;
        FirstRunningExecutor(CountDownLatch entered) { this.entered = entered; }
        @Override public void execute(Runnable command) {
            if (first != null) { queued.execute(command); return; }
            first = new Thread(command, "mip-test-extra-worker");
            first.setDaemon(true);
            first.start();
            await(entered);
        }
        void join() throws InterruptedException { first.join(5000); assertFalse(first.isAlive()); }
    }
}
