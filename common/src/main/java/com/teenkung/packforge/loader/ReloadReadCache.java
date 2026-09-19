package com.teenkung.packforge.loader;

import com.teenkung.packforge.concurrent.PreparationBudget;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Reload-local reuse for explicitly eligible stable resources. The first access only records
 * a bounded key; the second buffers the resource. All source I/O and flight waits happen
 * outside the metadata lock. Closing this cache does not retire its shared budget scope.
 *
 * Accounting includes payload capacity and conservative bookkeeping allowances, not a JVM
 * heap-size measurement. Caller-owned resource owners/paths are not copied or charged in full.
 */
public final class ReloadReadCache implements AutoCloseable {
    public static final int MAX_PAYLOAD = 256 * 1024;
    private static final long ENTRY_OVERHEAD = 512;
    private static final long PAYLOAD_OVERHEAD = 256;
    private static final long COUNTER_OVERHEAD = 512;
    private final PreparationBudget.Scope scope;
    private volatile Counters counters;
    private PreparationBudget.Reservation counterReservation;
    private Map<Key, Entry> entries = new HashMap<>();
    private boolean retired;

    @FunctionalInterface
    public interface StreamSource { InputStream open() throws IOException; }

    public ReloadReadCache(PreparationBudget.Scope scope) { this(scope, false); }

    /** Diagnostics are explicit per reload, normally captured from the existing loader-timings flag. */
    public ReloadReadCache(PreparationBudget.Scope scope, boolean diagnostics) {
        this.scope = Objects.requireNonNull(scope);
        if (diagnostics) {
            counterReservation = scope.tryReserve(COUNTER_OVERHEAD);
            if (counterReservation != null) {
                try {
                    counters = new Counters();
                } catch (RuntimeException | Error failure) {
                    counterReservation.close();
                    counterReservation = null;
                    throw failure;
                }
            }
        }
    }

    /**
     * Cumulative reload counters; cachedPayloadBytes is admitted payload volume, not live heap size.
     * Snapshot before close: closing discards the charged counter storage. Disabled also signals
     * insufficient diagnostic-memory admission, so it must not be interpreted as measured zero hits.
     */
    public synchronized Statistics statistics() {
        return counters == null ? new Statistics(false, 0, 0, 0, 0, 0, 0, 0, 0) : counters.snapshot();
    }

    public InputStream open(Object owner, String path, long knownLength, boolean eligible,
                            StreamSource source) throws IOException {
        Objects.requireNonNull(source);
        if (!eligible || owner == null || path == null || knownLength < 0 || knownLength > MAX_PAYLOAD) {
            return openOriginal(source);
        }
        increment(Counter.ELIGIBLE_CALLS);
        Key key = new Key(owner, path);
        Entry producer = null;
        CompletableFuture<Void> waitFor = null;
        synchronized (this) {
            if (!retired && !scope.isRetired()) {
                Entry entry = entries.get(key);
                if (entry == null) {
                    increment(Counter.FIRST_USE_BYPASSES);
                    PreparationBudget.Reservation tracking = scope.tryReserve(ENTRY_OVERHEAD + 2L * path.length());
                    if (tracking != null) entries.put(key, new Entry(tracking, knownLength));
                    else recordBudgetBypass();
                } else if (entry.length == knownLength) {
                    if (entry.data != null) {
                        InputStream borrowed = entry.data.borrow();
                        if (borrowed != null) {
                            increment(Counter.HITS);
                            return borrowed;
                        }
                        recordBudgetBypass();
                    } else if (entry.flight != null) {
                        waitFor = entry.flight;
                        increment(Counter.COALESCED_WAITS);
                    }
                    else {
                        entry.flight = new CompletableFuture<>();
                        producer = entry;
                    }
                }
            }
        }
        if (waitFor != null) {
            await(waitFor);
            // Borrow only while the cache still owns the data. Retirement may have discarded it.
            synchronized (this) {
                Entry entry = entries.get(key);
                if (!retired && !scope.isRetired() && entry != null && entry.data != null) {
                    InputStream borrowed = entry.data.borrow();
                    if (borrowed != null) {
                        increment(Counter.HITS);
                        return borrowed;
                    }
                    recordBudgetBypass();
                }
            }
            return openOriginal(source);
        }
        if (producer == null) return openOriginal(source);
        return load(key, producer, source);
    }

    private InputStream load(Key key, Entry entry, StreamSource source) throws IOException {
        PreparationBudget.Reservation allocation = scope.tryReserve(entry.length + 1 + PAYLOAD_OVERHEAD);
        if (allocation == null) {
            recordBudgetBypass();
            finish(key, entry, null, null);
            return openOriginal(source);
        }
        InputStream original = null;
        try {
            original = Objects.requireNonNull(openOriginal(source), "Source returned null stream");
            byte[] bytes = new byte[(int) entry.length + 1];
            int count = 0;
            while (count < bytes.length) {
                int read = original.read(bytes, count, bytes.length - count);
                if (read < 0) break;
                if (read == 0) {
                    int single = original.read();
                    if (single < 0) break;
                    bytes[count++] = (byte) single;
                } else count += read;
            }
            if (count != entry.length) {
                // Do not truncate a lying source or reopen it: return the consumed prefix plus tail.
                InputStream remainder = new SequenceInputStream(new ByteArrayInputStream(bytes, 0, count), original);
                original = null;
                InputStream result = leased(remainder, allocation);
                allocation = null;
                finish(key, entry, null, null);
                return result;
            }
            InputStream completed = original;
            original = null;
            completed.close();
            Data data = new Data(bytes, count, allocation, scope);
            allocation = null;
            InputStream result = data.borrowInitial();
            finish(key, entry, data, null);
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            increment(Counter.CACHE_FILL_FAILURES);
            try {
                if (original != null) {
                    try {
                        original.close();
                    } catch (IOException | RuntimeException | Error closeFailure) {
                        if (failure != closeFailure) failure.addSuppressed(closeFailure);
                    }
                }
            } finally {
                // Cleanup failures must never strand coalesced readers on an unfinished flight.
                finish(key, entry, null, failure);
            }
            throw failure;
        } finally {
            if (allocation != null) allocation.close();
        }
    }

    private void finish(Key key, Entry entry, Data data, Throwable failure) {
        CompletableFuture<Void> flight;
        synchronized (this) {
            flight = entry.flight;
            entry.flight = null;
            if (!retired && !scope.isRetired() && entries.get(key) == entry) {
                entry.data = data;
                if (data != null && counters != null) counters.cachedPayloadBytes += data.length;
            }
            else {
                entries.remove(key, entry);
                entry.tracking.close();
                if (data != null) data.release();
            }
        }
        if (failure == null) flight.complete(null);
        else flight.completeExceptionally(failure);
    }

    private InputStream openOriginal(StreamSource source) throws IOException {
        increment(Counter.SOURCE_OPENS);
        return source.open();
    }

    private void recordBudgetBypass() {
        increment(Counter.BUDGET_BYPASSES);
    }

    private void increment(Counter counter) {
        if (counters == null) return;
        synchronized (this) {
            // No counter reference escapes this lock; close can discard its charged storage.
            if (counters != null) counters.increment(counter);
        }
    }

    /** Source opens include original bypasses; failures count failed eager cache fills only. */
    public record Statistics(boolean enabled, long eligibleCalls, long firstUseBypasses, long hits,
                             long coalescedWaits, long sourceOpens, long cacheFillFailures,
                             long cachedPayloadBytes, long budgetBypasses) {}

    private static final class Counters {
        long eligibleCalls;
        long firstUseBypasses;
        long hits;
        long coalescedWaits;
        long sourceOpens;
        long cacheFillFailures;
        long cachedPayloadBytes;
        long budgetBypasses;

        void increment(Counter counter) {
            switch (counter) {
                case ELIGIBLE_CALLS -> eligibleCalls++;
                case FIRST_USE_BYPASSES -> firstUseBypasses++;
                case HITS -> hits++;
                case COALESCED_WAITS -> coalescedWaits++;
                case SOURCE_OPENS -> sourceOpens++;
                case CACHE_FILL_FAILURES -> cacheFillFailures++;
                case BUDGET_BYPASSES -> budgetBypasses++;
            }
        }

        Statistics snapshot() {
            return new Statistics(true, eligibleCalls, firstUseBypasses, hits,
                coalescedWaits, sourceOpens, cacheFillFailures, cachedPayloadBytes, budgetBypasses);
        }
    }

    private enum Counter {
        ELIGIBLE_CALLS, FIRST_USE_BYPASSES, HITS, COALESCED_WAITS, SOURCE_OPENS,
        CACHE_FILL_FAILURES, BUDGET_BYPASSES
    }

    private static void await(CompletableFuture<Void> flight) throws IOException {
        try { flight.get(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for resource read", interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IOException("Resource read failed", cause);
        }
    }

    @Override public synchronized void close() {
        if (retired) return;
        retired = true;
        for (Entry entry : entries.values()) {
            if (entry.data != null) entry.data.release();
            if (entry.flight == null) entry.tracking.close();
        }
        entries = new HashMap<>();
        counters = null;
        if (counterReservation != null) {
            counterReservation.close();
            counterReservation = null;
        }
    }

    private static InputStream leased(InputStream input, AutoCloseable lease) {
        return new LeasedInputStream(input, lease);
    }

    private static final class LeasedInputStream extends FilterInputStream {
        private boolean closed;
        private AutoCloseable activeLease;
        LeasedInputStream(InputStream input, AutoCloseable lease) { super(input); activeLease = lease; }
        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            try { super.close(); }
            finally {
                in = InputStream.nullInputStream();
                AutoCloseable releasing = activeLease;
                activeLease = null;
                try { releasing.close(); }
                catch (RuntimeException failure) { throw failure; }
                catch (Exception failure) { throw new IOException(failure); }
            }
        }
    }

    private record Key(Object owner, String path) {
        @Override public boolean equals(Object other) {
            return other instanceof Key key && owner == key.owner && path.equals(key.path);
        }
        @Override public int hashCode() { return 31 * System.identityHashCode(owner) + path.hashCode(); }
    }

    private static final class Entry {
        final PreparationBudget.Reservation tracking;
        final long length;
        CompletableFuture<Void> flight;
        Data data;
        Entry(PreparationBudget.Reservation tracking, long length) { this.tracking = tracking; this.length = length; }
    }

    private static final class Data {
        final byte[] bytes;
        final int length;
        final PreparationBudget.Reservation allocation;
        final PreparationBudget.Scope scope;
        long references = 1;
        Data(byte[] bytes, int length, PreparationBudget.Reservation allocation, PreparationBudget.Scope scope) {
            this.bytes = bytes; this.length = length; this.allocation = allocation; this.scope = scope;
        }
        synchronized InputStream borrow() {
            PreparationBudget.Reservation cursor = scope.tryReserve(128);
            if (cursor == null) return null;
            references++;
            return leased(new ByteArrayInputStream(bytes, 0, length), () -> { release(); cursor.close(); });
        }
        synchronized InputStream borrowInitial() {
            // Its cursor allowance is included in the payload reservation, including after retirement.
            references++;
            return leased(new ByteArrayInputStream(bytes, 0, length), this::release);
        }
        synchronized void release() { if (--references == 0) allocation.close(); }
    }
}
