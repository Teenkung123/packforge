package com.teenkung.packforge.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Non-blocking, shared accounting for preparation data across overlapping reloads. */
public final class PreparationBudget {
    public static final long DEFAULT_LIMIT = 128L * 1024 * 1024;
    private final long limit;
    private long used;
    private long peak;

    public PreparationBudget() { this(DEFAULT_LIMIT); }

    public PreparationBudget(long limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative budget");
        this.limit = limit;
    }

    public Scope openScope() { return openScope(limit); }

    public Scope openScope(long scopeLimit) {
        if (scopeLimit < 0 || scopeLimit > limit) throw new IllegalArgumentException("Invalid scope limit");
        return new Scope(scopeLimit);
    }

    public long limit() { return limit; }
    public synchronized long used() { return used; }
    public synchronized long peak() { return peak; }

    public final class Scope implements AutoCloseable {
        private final long scopeLimit;
        private long scopeUsed;
        private boolean retired;
        private List<Runnable> retirementActions = new ArrayList<>();

        private Scope(long scopeLimit) { this.scopeLimit = scopeLimit; }

        public Reservation tryReserve(long bytes) {
            if (bytes < 0) throw new IllegalArgumentException("Negative reservation");
            synchronized (PreparationBudget.this) {
                if (retired || bytes > limit - used || bytes > scopeLimit - scopeUsed) return null;
                used += bytes;
                scopeUsed += bytes;
                peak = Math.max(peak, used);
                return new Reservation(this, bytes);
            }
        }

        public long used() { synchronized (PreparationBudget.this) { return scopeUsed; } }
        public boolean isRetired() { synchronized (PreparationBudget.this) { return retired; } }
        /** Cleanup runs outside the accounting lock, once per registered owner. */
        public void onRetire(Runnable action) {
            Objects.requireNonNull(action);
            synchronized (PreparationBudget.this) {
                if (!retired) {
                    retirementActions.add(action);
                    return;
                }
            }
            action.run();
        }

        public void retire() {
            List<Runnable> actions;
            synchronized (PreparationBudget.this) {
                if (retired) return;
                retired = true;
                actions = retirementActions;
                retirementActions = List.of();
            }
            Throwable failure = null;
            for (Runnable action : actions) {
                try {
                    action.run();
                } catch (RuntimeException | Error error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
        }
        @Override public void close() { retire(); }
    }

    public final class Reservation implements AutoCloseable {
        private final Scope scope;
        private long bytes;
        private boolean closed;

        private Reservation(Scope scope, long bytes) { this.scope = scope; this.bytes = bytes; }
        public long bytes() { synchronized (PreparationBudget.this) { return bytes; } }

        /** Release temporary workspace without an unaccounted close/reacquire gap. */
        public void reduceTo(long retainedBytes) {
            synchronized (PreparationBudget.this) {
                if (retainedBytes < 0 || retainedBytes > bytes) {
                    throw new IllegalArgumentException("Reservation reduction must not grow the allocation");
                }
                if (closed) return;
                long released = bytes - retainedBytes;
                bytes = retainedBytes;
                used -= released;
                scope.scopeUsed -= released;
            }
        }

        @Override public void close() {
            synchronized (PreparationBudget.this) {
                if (closed) return;
                closed = true;
                used -= bytes;
                scope.scopeUsed -= bytes;
            }
        }
    }
}
