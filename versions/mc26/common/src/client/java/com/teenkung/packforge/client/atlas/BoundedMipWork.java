package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.concurrent.PreparationBudget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** Additional mip workers borrow the caller's executor; the caller always drains the remaining work. */
final class BoundedMipWork<T> {
    private static final Map<PreparationBudget.Scope, Admission> ADMISSIONS = new IdentityHashMap<>();
    private static final int MAX_EXTRA_WORKERS = 7;
    private final PreparationBudget.Scope budget;
    private final PreparationBudget.Reservation bookkeeping;
    private final List<T> inputs;
    private final Executor executor;
    private final BooleanSupplier fontPending;
    private final ToLongFunction<T> scratchBytes;
    private final Consumer<T> action;
    private final Consumer<Runnable> observeWorker;
    private final List<QueuedWorker> queued = new ArrayList<>();
    private final CompletableFuture<Void> future = new CompletableFuture<>();
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private int next;
    private int remaining;
    private int worked;
    private int startedWorkers;
    private int rejectedWorkers;
    private int failureIndex = Integer.MAX_VALUE;
    private Throwable failure;
    private boolean stopped;
    private boolean started;
    private boolean finished;

    static <T> BoundedMipWork<T> prepare(Collection<T> source, Executor executor, int workerBudget,
            PreparationBudget.Scope budget, BooleanSupplier fontPending, Predicate<List<T>> eligible,
            ToLongFunction<T> scratchBytes, Consumer<T> action, Consumer<Runnable> observeWorker) {
        if (source.size() < 2 || workerBudget < 2 || budget.isRetired() || fontPending.getAsBoolean()) return null;
        // Snapshot, temporary identity maps, queue tickets, closures, futures, and worker bookkeeping.
        PreparationBudget.Reservation memory = budget.tryReserve(4096L + 192L * source.size());
        if (memory == null) return null;
        boolean transferred = false;
        try {
            List<T> inputs = new ArrayList<>(source);
            if (!eligible.test(inputs)) return null;
            Admission admission = admission(budget, Math.min(MAX_EXTRA_WORKERS, workerBudget - 1));
            if (admission == null) return null;
            BoundedMipWork<T> work = new BoundedMipWork<>(budget, memory, inputs, executor, fontPending, scratchBytes, action, observeWorker);
            try {
                for (int index = 1; index < Math.min(4, workerBudget); index++) {
                    Lane lane = admission.tryAcquire();
                    if (lane == null) break;
                    try { work.queued.add(new QueuedWorker(work, lane)); }
                    catch (RuntimeException | Error failure) { lane.close(); throw failure; }
                }
            } catch (RuntimeException | Error failure) {
                for (QueuedWorker worker : work.queued) worker.abandon();
                throw failure;
            }
            if (work.queued.isEmpty()) return null;
            work.remaining = 1 + work.queued.size();
            transferred = true;
            return work;
        } finally {
            if (!transferred) memory.close();
        }
    }

    private BoundedMipWork(PreparationBudget.Scope budget, PreparationBudget.Reservation bookkeeping,
            List<T> inputs, Executor executor, BooleanSupplier fontPending, ToLongFunction<T> scratchBytes,
            Consumer<T> action, Consumer<Runnable> observeWorker) {
        this.budget = budget;
        this.bookkeeping = bookkeeping;
        this.inputs = inputs;
        this.executor = executor;
        this.fontPending = fontPending;
        this.scratchBytes = scratchBytes;
        this.action = action;
        this.observeWorker = observeWorker;
        future.whenComplete((ignored, error) -> { if (future.isCancelled()) stop(); });
    }

    void run() {
        synchronized (this) {
            if (started) throw new IllegalStateException("Mip work started twice");
            started = true;
        }
        // Submit at most three extra callbacks. None is required to start for the baseline to finish.
        for (QueuedWorker worker : queued) {
            if (isStopped()) { worker.disarm(); continue; }
            try { executor.execute(worker); }
            catch (RejectedExecutionException rejected) {
                // Optional capacity, not required work: baseline retains every unclaimed slot.
                // The ticket may already have run in an unusual inline executor; disarm is idempotent.
                worker.disarm();
                synchronized (this) { rejectedWorkers++; }
            }
            catch (RuntimeException | Error error) {
                fail(Integer.MAX_VALUE, error);
                worker.disarm();
            }
        }
        try { runWorker(false); }
        finally {
            // In single-thread/saturated executors these callbacks may never run. Atomically detach
            // their state and release lanes now. A callback which won the race counts as running.
            disarmQueued();
            workerFinished();
        }
    }

    /** Internal stop/result signal. Only drained() is an ownership barrier after cancellation. */
    CompletableFuture<Void> future() { return future; }
    CompletableFuture<Void> drained() { return drained; }
    synchronized int worked() { return worked; }
    synchronized int startedWorkers() { return startedWorkers; }
    synchronized int rejectedWorkers() { return rejectedWorkers; }

    private void runWorker(boolean extra) {
        try {
            observeWorker.accept(() -> {
                boolean claimedAny = false;
                while (true) {
                    Claim<T> claim;
                    try { claim = claim(extra); }
                    catch (RuntimeException | Error error) { fail(Integer.MAX_VALUE, error); throw error; }
                    if (claim == null) return;
                    if (!claimedAny) {
                        synchronized (this) { startedWorkers++; }
                        claimedAny = true;
                    }
                    try {
                        action.accept(claim.value);
                        synchronized (this) { worked++; }
                    } catch (RuntimeException | Error error) {
                        fail(claim.index, error);
                        throw error;
                    } finally {
                        if (claim.scratch != null) claim.scratch.close();
                    }
                }
            });
        } catch (RuntimeException | Error error) {
            fail(Integer.MAX_VALUE, error);
        }
    }

    private synchronized Claim<T> claim(boolean extra) {
        if (stopped || next == inputs.size()) return null;
        PreparationBudget.Reservation scratch = null;
        T value = inputs.get(next);
        if (extra) {
            if (budget.isRetired() || fontPending.getAsBoolean()) return null;
            scratch = budget.tryReserve(scratchBytes.applyAsLong(value));
            if (scratch == null) return null; // Baseline still claims this exact unconsumed slot.
        }
        return new Claim<>(next++, value, scratch);
    }

    private synchronized boolean isStopped() { return stopped; }

    private synchronized void fail(int index, Throwable error) {
        stopped = true;
        if (failure == null || index < failureIndex) {
            if (failure != null && failure != error) error.addSuppressed(failure);
            failure = error;
            failureIndex = index;
        } else if (failure != error) failure.addSuppressed(error);
    }

    private void stop() {
        synchronized (this) { stopped = true; }
        disarmQueued();
    }

    private void disarmQueued() { for (QueuedWorker worker : queued) worker.disarm(); }

    private void workerFinished() {
        Throwable terminal;
        synchronized (this) {
            if (--remaining != 0 || finished) return;
            finished = true;
            inputs.clear();
            terminal = failure;
        }
        bookkeeping.close();
        if (terminal == null) future.complete(null);
        else future.completeExceptionally(terminal);
        drained.complete(null);
    }

    private record Claim<T>(int index, T value, PreparationBudget.Reservation scratch) {}

    private static final class QueuedWorker implements Runnable {
        private final AtomicReference<Ticket> ticket;
        QueuedWorker(BoundedMipWork<?> owner, Lane lane) { ticket = new AtomicReference<>(new Ticket(owner, lane)); }
        @Override public void run() {
            Ticket claimed = ticket.getAndSet(null);
            if (claimed == null) return;
            try { claimed.owner.runWorker(true); }
            finally { claimed.lane.close(); claimed.owner.workerFinished(); }
        }
        void disarm() {
            Ticket pending = ticket.getAndSet(null);
            if (pending == null) return;
            pending.lane.close();
            pending.owner.workerFinished();
        }
        void abandon() {
            Ticket pending = ticket.getAndSet(null);
            if (pending != null) pending.lane.close();
        }
    }

    private record Ticket(BoundedMipWork<?> owner, Lane lane) {}

    private static Admission admission(PreparationBudget.Scope scope, int lanes) {
        synchronized (ADMISSIONS) {
            Admission existing = ADMISSIONS.get(scope);
            if (existing != null) return existing;
            PreparationBudget.Reservation memory = scope.tryReserve(512);
            if (memory == null) return null;
            Admission created = new Admission(scope, lanes, memory);
            ADMISSIONS.put(scope, created);
            scope.onRetire(created::retire);
            return created;
        }
    }

    private static final class Admission {
        private final PreparationBudget.Scope scope;
        private final int limit;
        private final PreparationBudget.Reservation memory;
        private int used;
        private boolean retired;
        Admission(PreparationBudget.Scope scope, int limit, PreparationBudget.Reservation memory) {
            this.scope = scope; this.limit = limit; this.memory = memory;
        }
        synchronized Lane tryAcquire() {
            if (retired || used == limit) return null;
            used++;
            return new Lane(this);
        }
        synchronized void release() { if (--used == 0 && retired) memory.close(); }
        void retire() {
            synchronized (ADMISSIONS) { ADMISSIONS.remove(scope, this); }
            synchronized (this) { retired = true; if (used == 0) memory.close(); }
        }
    }

    private static final class Lane implements AutoCloseable {
        private Admission owner;
        Lane(Admission owner) { this.owner = owner; }
        @Override public synchronized void close() {
            Admission release = owner;
            owner = null;
            if (release != null) release.release();
        }
    }
}
