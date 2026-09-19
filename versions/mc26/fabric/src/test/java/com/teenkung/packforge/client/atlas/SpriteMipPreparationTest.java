package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.config.OptimizationPlan;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real source/command/drain wrapper using a lightweight admitted owner adapter. */
class SpriteMipPreparationTest {
    private static final List<Integer> INPUT = IntStream.range(0, 64).boxed().toList();
    private final ReloadExecutionContext context = ReloadExecutionContext.startForTesting(snapshot());
    private final AtlasLoadInvocation atlas = new AtlasLoadInvocation(Identifier.fromNamespaceAndPath("test", "atlas"), false);

    @AfterEach void cleanup() { ReloadExecutionContext.finish(context); }

    @Test
    void commandCompletionCannotPublishReadinessBeforeOriginalSourceFuture() {
        HoldingExecutor executor = new HoldingExecutor();
        CompletableFuture<Void> source = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> result = submit(() -> loop(value -> {
            assertSame(context, ReloadExecutionContext.current());
            assertSame(atlas, AtlasLoadInvocation.current());
            calls.incrementAndGet();
        }), executor, (command, supplied) -> { supplied.execute(command); return source; });
        executor.runOne();
        assertEquals(64, calls.get());
        assertFalse(result.isDone());
        assertEquals(1024, context.preparationBudget().used(), "Wrapper and shared admission remain before source completion");
        source.complete(null);
        result.join();
        assertEquals(512, context.preparationBudget().used());
        assertNull(AtlasLoadInvocation.current());
        executor.runAll();
        assertEquals(64, calls.get());
    }

    @Test
    void sourceFailureAfterCommandCompletionRetainsOriginalCause() {
        HoldingExecutor executor = new HoldingExecutor();
        CompletableFuture<Void> source = new CompletableFuture<>();
        IllegalArgumentException failure = new IllegalArgumentException("original source completion context");
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> result = submit(() -> loop(value -> calls.incrementAndGet()), executor,
            (command, supplied) -> { supplied.execute(command); return source; });
        executor.runOne();
        assertFalse(result.isDone());
        source.completeExceptionally(failure);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        assertEquals(64, calls.get());
        assertEquals(512, context.preparationBudget().used());
    }

    @Test
    void originalCommandFailureRestoresBindingsAndReleasesWrapperReservation() {
        HoldingExecutor executor = new HoldingExecutor();
        IllegalStateException expected = new IllegalStateException("original runnable failure");
        CompletableFuture<Void> result = submit(() -> { throw expected; }, executor, CompletableFuture::runAsync);
        executor.runOne();
        assertSame(expected, assertThrows(CompletionException.class, result::join).getCause());
        assertEquals(0, context.preparationBudget().used());
        assertNull(AtlasLoadInvocation.current());
        assertFalse(SpriteMipPreparation.tryParallel(INPUT, value -> fail("Leaked scope"), values -> true, value -> 1));
    }

    @Test
    void originalSourceSubmissionRejectionStillThrowsAndReleasesWrapperReservation() {
        RejectedExecutionException expected = new RejectedExecutionException("original source rejected");
        AtomicInteger calls = new AtomicInteger();
        assertSame(expected, assertThrows(RejectedExecutionException.class, () -> submit(calls::incrementAndGet,
            Runnable::run, (command, supplied) -> { throw expected; })));
        assertEquals(0, calls.get());
        assertEquals(0, context.preparationBudget().used());
        assertNull(AtlasLoadInvocation.current());
    }

    @Test
    void cancellationBeforeOriginalCommandStartsDisarmsIt() {
        HoldingExecutor executor = new HoldingExecutor();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> result = submit(() -> loop(value -> calls.incrementAndGet()), executor, CompletableFuture::runAsync);
        assertEquals(512, context.preparationBudget().used());
        assertTrue(result.cancel(false));
        assertTrue(result.isDone(), "A true cancellation return must already be terminal");
        assertTrue(result.isCancelled());
        assertEquals(0, context.preparationBudget().used());
        executor.runAll();
        assertEquals(0, calls.get());
    }

    @Test
    void cancellationOfRunningOriginalCommandDoesNotReleaseItsReservationEarly() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Thread> thread = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> originalSource = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean ownerClosed = new AtomicBoolean(), checkedOwnerAlive = new AtomicBoolean();
        Executor executor = command -> {
            Thread created = new Thread(command, "mip-wrapper-original");
            created.setDaemon(true);
            thread.set(created);
            created.start();
        };
        CompletableFuture<Void> result = submit(() -> {
            entered.countDown();
            await(release);
            assertFalse(ownerClosed.get(), "Readiness cleanup closed the owner while its command still ran");
            checkedOwnerAlive.set(true);
            loop(value -> calls.incrementAndGet());
        }, executor, (command, supplied) -> {
            CompletableFuture<Void> source = CompletableFuture.runAsync(command, supplied);
            originalSource.set(source);
            return source;
        });
        result.whenComplete((ignored, failure) -> ownerClosed.set(true));
        try {
            await(entered);
            assertFalse(result.cancel(false), "Active cancellation cannot acknowledge a terminal future yet");
            assertFalse(result.isDone());
            assertFalse(result.isCancelled());
            assertFalse(ownerClosed.get());
            assertFalse(originalSource.get().isCancelled(), "A running source must not trigger its own early cleanup");
            assertEquals(512, context.preparationBudget().used());
        } finally { release.countDown(); thread.get().join(5000); }
        assertFalse(thread.get().isAlive());
        assertTrue(checkedOwnerAlive.get());
        assertTrue(ownerClosed.get());
        assertTrue(result.isDone());
        assertTrue(result.isCancelled());
        assertThrows(CancellationException.class, result::join);
        assertEquals(0, calls.get());
        assertEquals(0, context.preparationBudget().used());
    }

    @Test
    void originalSourceCancellationWaitsForCommandDrainAndStopsLaterOptionalWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CompletableFuture<Void> source = new CompletableFuture<>();
        AtomicReference<Thread> thread = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        Executor executor = command -> {
            Thread created = new Thread(command, "mip-wrapper-source-cancellation");
            created.setDaemon(true);
            thread.set(created);
            created.start();
        };
        CompletableFuture<Void> result = submit(() -> {
            entered.countDown();
            await(release);
            loop(value -> calls.incrementAndGet());
        }, executor, (command, supplied) -> { supplied.execute(command); return source; });
        try {
            await(entered);
            assertTrue(source.cancel(false));
            assertFalse(result.isDone());
            assertEquals(512, context.preparationBudget().used());
        } finally { release.countDown(); thread.get().join(5000); }
        assertFalse(thread.get().isAlive());
        assertTrue(result.isCancelled());
        assertEquals(0, calls.get());
        assertEquals(0, context.preparationBudget().used());
    }

    @Test
    void sourceCompletionCannotPublishReadinessBeforeRunningChildDrains() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ControlledExecutor executor = new ControlledExecutor(entered);
        AtomicReference<CompletableFuture<Void>> source = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> result = submit(() -> loop(value -> {
            assertSame(context, ReloadExecutionContext.current());
            assertSame(atlas, AtlasLoadInvocation.current());
            if (value == 0) { entered.countDown(); await(release); }
            calls.incrementAndGet();
        }), executor, (command, supplied) -> {
            CompletableFuture<Void> original = CompletableFuture.runAsync(command, supplied);
            source.set(original);
            return original;
        });
        try {
            executor.runOriginal();
            assertTrue(source.get().isDone());
            assertFalse(result.isDone());
            assertEquals(63, calls.get());
            assertTrue(context.preparationBudget().used() >= 1024 * 1024);
        } finally { release.countDown(); executor.join(); }
        result.join();
        assertEquals(64, calls.get());
        assertEquals(512, context.preparationBudget().used());
        executor.pending.runAll();
        assertEquals(64, calls.get());
    }

    @Test
    void childFailureSurvivesSuccessfulSourceFutureWithoutPrematureRelease() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ControlledExecutor executor = new ControlledExecutor(entered);
        IllegalStateException expected = new IllegalStateException("original consumer resource context");
        CompletableFuture<Void> result = submit(() -> loop(value -> {
            if (value == 0) { entered.countDown(); await(release); throw expected; }
        }), executor, CompletableFuture::runAsync);
        try {
            executor.runOriginal();
            assertFalse(result.isDone());
        } finally { release.countDown(); executor.join(); }
        assertSame(expected, assertThrows(CompletionException.class, result::join).getCause());
        assertEquals(512, context.preparationBudget().used());
    }

    @Test
    void wrapperCancellationRetainsRunningChildScratchUntilItFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ControlledExecutor executor = new ControlledExecutor(entered);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean ownerClosed = new AtomicBoolean(), checkedOwnerAlive = new AtomicBoolean();
        CompletableFuture<Void> result = submit(() -> loop(value -> {
            if (value == 0) {
                entered.countDown();
                await(release);
                assertFalse(ownerClosed.get(), "Readiness cleanup closed the owner while a child still accessed it");
                checkedOwnerAlive.set(true);
            }
            calls.incrementAndGet();
        }), executor, CompletableFuture::runAsync);
        result.whenComplete((ignored, failure) -> ownerClosed.set(true));
        try {
            executor.runOriginal();
            assertFalse(result.cancel(false));
            assertFalse(result.isDone());
            assertFalse(result.isCancelled());
            assertFalse(ownerClosed.get());
            assertFalse(result.cancel(false), "Repeated cooperative requests remain nonterminal while the owner is active");
            assertTrue(context.preparationBudget().used() >= 1024 * 1024);
        } finally { release.countDown(); executor.join(); }
        assertTrue(checkedOwnerAlive.get());
        assertTrue(ownerClosed.get());
        assertTrue(result.isDone());
        assertTrue(result.isCancelled());
        assertThrows(CancellationException.class, result::join);
        assertEquals(64, calls.get());
        assertEquals(512, context.preparationBudget().used());
        executor.pending.runAll();
        assertEquals(64, calls.get());
    }

    @Test
    void optionalChildRejectionAfterOneStartsDoesNotFailSuccessfulOriginalSource() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ControlledExecutor executor = new ControlledExecutor(entered, true);
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> result = submit(() -> loop(value -> {
            if (value == 0) { entered.countDown(); await(release); }
            calls.incrementAndGet();
        }), executor, CompletableFuture::runAsync);
        try {
            executor.runOriginal();
            assertEquals(63, calls.get());
            assertFalse(result.isDone());
        } finally { release.countDown(); executor.join(); }
        result.join();
        assertEquals(64, calls.get());
        assertEquals(512, context.preparationBudget().used());
    }

    @Test
    void budgetRetirementDuringOriginalRunnableDoesNotBecomeCancellation() {
        HoldingExecutor executor = new HoldingExecutor();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> result = submit(() -> loop(value -> {
            if (value == 0) context.preparationBudget().retire();
            calls.incrementAndGet();
        }), executor, CompletableFuture::runAsync);
        executor.runOne();
        result.join();
        assertEquals(64, calls.get());
        assertFalse(result.isCancelled());
        assertEquals(0, context.preparationBudget().used());
        executor.runAll();
    }

    @Test
    void unadmittedLoopExecutesCompleteOriginalConsumerPath() {
        HoldingExecutor executor = new HoldingExecutor();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> result = submit(() -> {
            Consumer<Integer> original = value -> calls.incrementAndGet();
            if (!SpriteMipPreparation.tryParallel(INPUT, original, values -> false, value -> 1024L * 1024)) INPUT.forEach(original);
        }, executor, CompletableFuture::runAsync);
        executor.runOne();
        result.join();
        assertEquals(64, calls.get());
        assertEquals(0, context.preparationBudget().used());
        assertTrue(executor.queue.isEmpty());
    }

    private CompletableFuture<Void> submit(Runnable command, Executor raw,
            BiFunction<Runnable, Executor, CompletableFuture<Void>> original) {
        Executor supplied = atlas.bind(task -> raw.execute(ReloadExecutionContext.bindRunnable(context, task)));
        try (ReloadExecutionContext.Scope ignored = ReloadExecutionContext.bind(context)) {
            return atlas.call(() -> SpriteMipPreparation.submit(command, supplied, (task, actual) -> {
                assertSame(supplied, actual, "Wrapper must preserve the supplied executor");
                return original.apply(task, actual);
            }));
        }
    }

    private static void loop(Consumer<Integer> original) {
        if (!SpriteMipPreparation.tryParallel(INPUT, original, values -> true, value -> 1024L * 1024)) INPUT.forEach(original);
    }

    private static ReloadFeatureSnapshot snapshot() {
        return new ReloadFeatureSnapshot(false, false, false, false, false, false, false, false, false,
            false, false, false, false, 1, false, false, false, false, false, false, false, false, 1,
            true, 1, false, 1, Set.of(), false, 1, false, false, false, false, false, false, false,
            0, Thread.NORM_PRIORITY, false, false, false, false, false, 8, false, 128, OptimizationPlan.capture(PackForgeConfig.get()));
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "Worker rendezvous timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    private static final class HoldingExecutor implements Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable command) { queue.add(command); }
        void runOne() { queue.remove().run(); }
        void runAll() { while (!queue.isEmpty()) runOne(); }
    }

    private static final class ControlledExecutor implements Executor {
        final HoldingExecutor pending = new HoldingExecutor();
        final CountDownLatch entered;
        final boolean rejectAfterFirstChild;
        Runnable original;
        Thread child;
        ControlledExecutor(CountDownLatch entered) { this(entered, false); }
        ControlledExecutor(CountDownLatch entered, boolean rejectAfterFirstChild) {
            this.entered = entered; this.rejectAfterFirstChild = rejectAfterFirstChild;
        }
        @Override public void execute(Runnable command) {
            if (original == null) { original = command; return; }
            if (child != null) {
                if (rejectAfterFirstChild) throw new RejectedExecutionException("optional child rejected");
                pending.execute(command); return;
            }
            child = new Thread(command, "mip-wrapper-extra");
            child.setDaemon(true);
            child.start();
            await(entered);
        }
        void runOriginal() { original.run(); }
        void join() throws InterruptedException { child.join(5000); assertFalse(child.isAlive()); }
    }
}
