package com.teenkung.packforge.loader;

import com.teenkung.packforge.concurrent.PreparationBudget;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReloadReadCacheTest {
    private static InputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    private static String read(InputStream input) throws IOException {
        try (input) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Test void diagnosticsAreExplicitAndCountPhysicalReadsRatherThanOnlyCacheFills() throws Exception {
        var budget = new PreparationBudget();
        var plain = new ReloadReadCache(budget.openScope());
        assertFalse(plain.statistics().enabled());
        plain.close();
        var measured = new ReloadReadCache(budget.openScope(), true);
        for (int i = 0; i < 3; i++) assertEquals("abc", read(measured.open(this, "x", 3, true, () -> bytes("abc"))));
        assertEquals("abc", read(measured.open(this, "dynamic", -1, false, () -> bytes("abc"))));
        var stats = measured.statistics();
        assertTrue(stats.enabled());
        assertEquals(3, stats.eligibleCalls());
        assertEquals(1, stats.firstUseBypasses());
        assertEquals(1, stats.hits());
        assertEquals(3, stats.sourceOpens());
        assertEquals(3, stats.cachedPayloadBytes());
        assertEquals(0, stats.coalescedWaits());
        assertEquals(0, stats.cacheFillFailures());
        assertEquals(0, stats.budgetBypasses());
        measured.close();
    }

    @Test void diagnosticsDistinguishFailedFillsAndMemoryBypassesFromHits() throws Exception {
        var budget = new PreparationBudget();
        var measured = new ReloadReadCache(budget.openScope(), true);
        read(measured.open(this, "x", 3, true, () -> bytes("abc")));
        assertThrows(IOException.class, () -> measured.open(this, "x", 3, true, () -> { throw new IOException("failure"); }));
        assertEquals(1, measured.statistics().cacheFillFailures());
        assertEquals(2, measured.statistics().sourceOpens());
        assertEquals(0, measured.statistics().hits());
        measured.close();
        var limited = new ReloadReadCache(new PreparationBudget(1028).openScope(), true);
        read(limited.open(this, "x", 3, true, () -> bytes("abc")));
        read(limited.open(this, "x", 3, true, () -> bytes("abc")));
        assertEquals(1, limited.statistics().budgetBypasses());
        assertEquals(2, limited.statistics().sourceOpens());
        assertEquals(0, limited.statistics().cachedPayloadBytes());
        limited.close();
    }

    @Test void diagnosticStorageUsesSharedAdmissionAndIsDiscardedAtClose() {
        var budget = new PreparationBudget(512);
        var scope = budget.openScope();
        var measured = new ReloadReadCache(scope, true);
        assertTrue(measured.statistics().enabled());
        assertEquals(512, budget.used());
        assertFalse(new ReloadReadCache(scope, true).statistics().enabled(),
            "Missing counter capacity must not be reported as a measured zero-hit run");
        scope.retire();
        assertEquals(512, budget.used(), "Retirement alone does not discard the cache's counters");
        measured.close();
        assertEquals(0, budget.used());
        assertFalse(measured.statistics().enabled(), "Snapshot diagnostics before closing the cache");
        measured.close();
        assertEquals(0, budget.used());
    }

    @Test void firstUseIsOriginalThenIndependentCursorsWithIdentityKeys() throws Exception {
        var budget = new PreparationBudget();
        var cache = new ReloadReadCache(budget.openScope());
        Object owner = new String("same");
        AtomicInteger opens = new AtomicInteger();
        ReloadReadCache.StreamSource source = () -> { opens.incrementAndGet(); return bytes("abc"); };
        InputStream original = bytes("abc");
        assertSame(original, cache.open(owner, "x.json", 3, true, () -> original));
        original.close();
        try (var a = cache.open(owner, "x.json", 3, true, source);
             var b = cache.open(owner, "x.json", 3, true, source)) {
            assertEquals('a', a.read());
            assertEquals('a', b.read());
        }
        assertEquals(1, opens.get());
        assertEquals("abc", read(cache.open(new String("same"), "x.json", 3, true, source)));
        assertEquals(2, opens.get());
        cache.close();
        assertEquals(0, budget.used());
    }

    @Test void concurrentRepeatsCoalesceAndBorrowedStreamsOutliveRetirement() throws Exception {
        var budget = new PreparationBudget();
        var scope = budget.openScope();
        var cache = new ReloadReadCache(scope);
        Object owner = new Object();
        read(cache.open(owner, "x", 3, true, () -> bytes("abc")));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        AtomicInteger opens = new AtomicInteger();
        ReloadReadCache.StreamSource source = () -> {
            opens.incrementAndGet(); entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            return bytes("abc");
        };
        List<Future<InputStream>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> cache.open(owner, "x", 3, true, source)));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            release.countDown();
            List<InputStream> streams = new ArrayList<>();
            for (var result : results) streams.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(1, opens.get());
            cache.close(); scope.retire();
            assertTrue(budget.used() > 0);
            for (var stream : streams) assertEquals("abc", read(stream));
            assertEquals(0, budget.used());
        } finally { release.countDown(); pool.shutdownNow(); cache.close(); }
    }

    @Test void failedLoadsRetryAndMisreportedLengthsNeverTruncate() throws Exception {
        var budget = new PreparationBudget();
        var cache = new ReloadReadCache(budget.openScope());
        Object owner = new Object();
        read(cache.open(owner, "x", 3, true, () -> bytes("abc")));
        assertThrows(IOException.class, () -> cache.open(owner, "x", 3, true, () -> { throw new IOException("failure"); }));
        assertEquals("abc", read(cache.open(owner, "x", 3, true, () -> bytes("abc"))));
        for (long declared : new long[]{0, 2, 6}) {
            String key = "lie" + declared;
            read(cache.open(owner, key, declared, true, () -> bytes("abc")));
            assertEquals("abc", read(cache.open(owner, key, declared, true, () -> bytes("abc"))));
        }
        cache.close();
        assertEquals(0, budget.used());
    }

    @Test void ineligibleAndExhaustedResourcesBypassWithoutReading() throws Exception {
        var budget = new PreparationBudget(0);
        var cache = new ReloadReadCache(budget.openScope());
        for (long length : new long[]{-1, 3, ReloadReadCache.MAX_PAYLOAD + 1L}) {
            InputStream original = bytes("abc");
            assertSame(original, cache.open(this, "x", length, true, () -> original));
            original.close();
        }
        var normalBudget = new PreparationBudget();
        var normal = new ReloadReadCache(normalBudget.openScope());
        InputStream original = bytes("abc");
        assertSame(original, normal.open(this, "x", 3, false, () -> original));
        original.close();
        assertEquals(0, normalBudget.used());
        cache.close(); normal.close();
    }

    @Test void staleCompletionReturnsLiveCallerBytesWithoutPublication() throws Exception {
        var budget = new PreparationBudget();
        var scope = budget.openScope();
        var cache = new ReloadReadCache(scope);
        read(cache.open(this, "x", 3, true, () -> bytes("abc")));
        InputStream live = cache.open(this, "x", 3, true, () -> {
            cache.close(); scope.retire();
            return bytes("abc");
        });
        assertTrue(budget.used() > 0);
        assertEquals("abc", read(live));
        assertEquals(0, budget.used());
        assertEquals("new", read(cache.open(this, "x", 3, true, () -> bytes("new"))));
    }

    @Test void concurrentFailureFansOutAndLaterCallRetries() throws Exception {
        var budget = new PreparationBudget();
        var cache = new ReloadReadCache(budget.openScope());
        read(cache.open(this, "x", 3, true, () -> bytes("abc")));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new IOException("one shared failure");
        var producer = new FutureTask<InputStream>(() -> cache.open(this, "x", 3, true, () -> {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            throw failure;
        }));
        Thread producing = new Thread(producer);
        producing.start();
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        var waiter = new FutureTask<InputStream>(() -> cache.open(this, "x", 3, true, () -> {
            throw new AssertionError("Waiter must not open a second source");
        }));
        Thread waiting = new Thread(waiter);
        waiting.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (waiting.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield();
            assertEquals(Thread.State.WAITING, waiting.getState());
            release.countDown();
            assertSame(failure, assertThrows(ExecutionException.class,
                () -> producer.get(10, TimeUnit.SECONDS)).getCause());
            assertSame(failure, assertThrows(ExecutionException.class,
                () -> waiter.get(10, TimeUnit.SECONDS)).getCause());
            assertEquals("abc", read(cache.open(this, "x", 3, true, () -> bytes("abc"))));
        } finally { release.countDown(); producing.join(10000); waiting.join(10000); cache.close(); }
        assertEquals(0, budget.used());
    }

    @Test void readAndCleanupFailuresAlwaysCompleteWaitingReaders() throws Exception {
        for (Throwable separateCloseFailure : new Throwable[]{null,
            new IllegalStateException("runtime close failure"), new AssertionError("error close failure")}) {
            var budget = new PreparationBudget();
            var cache = new ReloadReadCache(budget.openScope(), true);
            read(cache.open(this, "x", 3, true, () -> bytes("abc")));
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var readFailure = new IOException("read failure");
            Throwable closeFailure = separateCloseFailure == null ? readFailure : separateCloseFailure;
            var closes = new AtomicInteger();
            var producer = new FutureTask<InputStream>(() -> cache.open(this, "x", 3, true, () -> new InputStream() {
                @Override public int read() throws IOException {
                    entered.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out"); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new IOException(interrupted);
                    }
                    throw readFailure;
                }

                @Override public void close() throws IOException {
                    closes.incrementAndGet();
                    if (closeFailure instanceof IOException io) throw io;
                    if (closeFailure instanceof RuntimeException runtime) throw runtime;
                    throw (Error) closeFailure;
                }
            }));
            var waiter = new FutureTask<InputStream>(() -> cache.open(this, "x", 3, true, () -> {
                throw new AssertionError("Waiter must share the failing flight");
            }));
            Thread producing = new Thread(producer);
            Thread waiting = new Thread(waiter);
            producing.start();
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                waiting.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (waiting.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield();
                assertEquals(Thread.State.WAITING, waiting.getState());
                release.countDown();
                assertSame(readFailure, assertThrows(ExecutionException.class,
                    () -> producer.get(10, TimeUnit.SECONDS)).getCause());
                assertSame(readFailure, assertThrows(ExecutionException.class,
                    () -> waiter.get(10, TimeUnit.SECONDS)).getCause());
                assertEquals(1, closes.get());
                if (separateCloseFailure == null) assertEquals(0, readFailure.getSuppressed().length);
                else assertArrayEquals(new Throwable[]{closeFailure}, readFailure.getSuppressed());
                assertEquals(1, cache.statistics().cacheFillFailures());
                assertEquals(1, cache.statistics().coalescedWaits());
                assertEquals("abc", read(cache.open(this, "x", 3, true, () -> bytes("abc"))));
            } finally {
                release.countDown();
                // Interrupt also releases a waiter if an assertion detects a broken future fanout.
                producing.interrupt(); waiting.interrupt();
                producing.join(10000); waiting.join(10000);
                cache.close();
            }
            assertEquals(0, budget.used());
        }
    }

    @Test void actualOversizedStreamAndEmptyPayloadKeepOriginalBytes() throws Exception {
        var budget = new PreparationBudget();
        var cache = new ReloadReadCache(budget.openScope());
        byte[] large = new byte[ReloadReadCache.MAX_PAYLOAD + 100];
        large[large.length - 1] = 99;
        read(cache.open(this, "large", 2, true, () -> new ByteArrayInputStream(large)));
        try (var stream = cache.open(this, "large", 2, true, () -> new ByteArrayInputStream(large))) {
            cache.close();
            assertTrue(budget.used() > 0);
            assertArrayEquals(large, stream.readAllBytes());
        }
        assertEquals(0, budget.used());
        var emptyCache = new ReloadReadCache(budget.openScope());
        for (int i = 0; i < 3; i++) assertEquals("", read(emptyCache.open(this, "empty", 0, true, () -> bytes(""))));
        emptyCache.close();
        assertEquals(0, budget.used());
    }

    @Test void payloadExhaustionBypassesAndKeyTrackingStaysBounded() throws Exception {
        var budget = new PreparationBudget(520);
        var cache = new ReloadReadCache(budget.openScope());
        read(cache.open(this, "x", 3, true, () -> bytes("abc")));
        InputStream original = bytes("abc");
        assertSame(original, cache.open(this, "x", 3, true, () -> original));
        original.close();
        for (int i = 0; i < 1000; i++) read(cache.open(this, "other" + i, 3, true, () -> bytes("abc")));
        assertTrue(budget.used() <= 520);
        cache.close();
        assertEquals(0, budget.used());
    }
}
