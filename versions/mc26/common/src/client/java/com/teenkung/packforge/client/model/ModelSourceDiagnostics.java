package com.teenkung.packforge.client.model;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadExecutionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/** Diagnostic-only observation of vanilla-resolved models; never reads ahead or caches model objects. */
public final class ModelSourceDiagnostics {
    private static final int MAX_SIGNATURES = 4096;
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    public static <T> CompletableFuture<T> observe(Executor executor, Function<Executor, CompletableFuture<T>> loader) {
        ReloadExecutionContext context = ReloadExecutionContext.current();
        if (!FeatureFlags.modelParseTimingEnabled() || context == null) return loader.apply(executor);
        PreparationBudget.Reservation reservation = context.preparationBudget().tryReserve(1024 * 1024);
        if (reservation == null) return loader.apply(executor);
        Session session = new Session(context, reservation);
        AtomicReference<Session> active = new AtomicReference<>(session);
        session.active = active;
        WeakReference<Session> retirement = new WeakReference<>(session);
        context.preparationBudget().onRetire(() -> {
            Session owner = retirement.get();
            if (owner != null) owner.finish(new CancellationException("Reload generation retired"));
        });
        Executor wrapped = command -> {
            Session owner = active.get();
            if (owner == null) executor.execute(command);
            else owner.submit(executor, command);
        };
        try {
            CompletableFuture<T> result = loader.apply(wrapped);
            return attach(session, result);
        } catch (RuntimeException | Error failure) {
            session.finish(failure);
            throw failure;
        }
    }

    static <T> CompletableFuture<T> attach(Session session, CompletableFuture<T> result) {
        result.whenComplete((value, error) -> session.finish(error));
        return result;
    }

    public static long start() { return CURRENT.get() == null ? 0 : System.nanoTime(); }

    public static void enumerated(int count, long start) {
        Session session = CURRENT.get();
        if (session == null || start == 0) return;
        session.enumerated.add(count);
        session.listNanos.add(System.nanoTime() - start);
    }

    public static <T> T parse(Reader reader, Function<Reader, T> original) {
        Session session = CURRENT.get();
        if (session == null) return original.apply(reader);
        if (reader == null || reader.getClass() != BufferedReader.class) {
            session.bypassed.increment();
            return original.apply(reader);
        }
        PreparationBudget.Reservation reservation = session.context.preparationBudget().tryReserve(2048);
        if (reservation == null) {
            session.bypassed.increment();
            return original.apply(reader);
        }
        try (reservation) {
            ObservedReader observed = new ObservedReader(reader);
            long start = System.nanoTime();
            boolean successful = false;
            try {
                T result = original.apply(observed);
                successful = true;
                return result;
            } finally {
                session.record(observed, successful, System.nanoTime() - start);
            }
        }
    }

    static final class Session {
        private final ReloadExecutionContext context;
        private final PreparationBudget.Reservation reservation;
        private Set<String> signatures = new HashSet<>();
        private AtomicReference<Session> active;
        private int pending;
        private final LongAdder enumerated = new LongAdder();
        private final LongAdder listNanos = new LongAdder();
        private final LongAdder calls = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder chars = new LongAdder();
        private final LongAdder readNanos = new LongAdder();
        private final LongAdder hashingNanos = new LongAdder();
        private final LongAdder parseNanos = new LongAdder();
        private final LongAdder eofObserved = new LongAdder();
        private final LongAdder prefixMatches = new LongAdder();
        private final LongAdder bypassed = new LongAdder();
        private boolean closed;

        Session(ReloadExecutionContext context, PreparationBudget.Reservation reservation) {
            this.context = context;
            this.reservation = reservation;
        }

        private synchronized void record(ObservedReader reader, boolean successful, long totalNanos) {
            if (closed) return;
            calls.increment();
            if (!successful) failures.increment();
            chars.add(reader.chars);
            readNanos.add(reader.readNanos);
            hashingNanos.add(reader.hashingNanos);
            parseNanos.add(totalNanos);
            if (reader.eof) eofObserved.increment();
            if (!successful || !reader.fingerprintValid) return;
            String signature = reader.chars + ":" + HexFormat.of().formatHex(reader.digest.digest());
            if (signatures.contains(signature)) prefixMatches.increment();
            else if (signatures.size() < MAX_SIGNATURES) signatures.add(signature);
            else bypassed.increment();
        }

        void submit(Executor executor, Runnable command) {
            Task task;
            synchronized (this) {
                PreparationBudget.Reservation taskLease = closed ? null : context.preparationBudget().tryReserve(256);
                if (taskLease == null) task = null;
                else {
                    pending++;
                    task = new Task(this, command, taskLease);
                }
            }
            if (task == null) {
                executor.execute(command);
                return;
            }
            try {
                executor.execute(task);
            } catch (RuntimeException | Error failure) {
                task.release();
                throw failure;
            }
        }

        private synchronized void released() {
            pending--;
            if (closed && pending == 0) reservation.close();
        }

        void finish(Throwable failure) {
            Object[] snapshot;
            synchronized (this) {
                if (closed) return;
                closed = true;
                if (active != null) active.set(null);
                snapshot = new Object[] {context.reloadId(), enumerated.sum(), calls.sum(), failures.sum(), chars.sum(), eofObserved.sum(),
                    prefixMatches.sum(), signatures.size(), bypassed.sum(), listNanos.sum(), readNanos.sum(),
                    hashingNanos.sum(), parseNanos.sum(), failure != null};
                signatures = Set.of();
                if (pending == 0) reservation.close();
            }
            PackForge.LOGGER.info("PackForge resolved model source diagnostics: reload={} enumerated={} calls={} failures={} charsRead={} eofObserved={} observedPrefixMatches={} signatureCount={} bypassed={} listNs={} readerNs={} fingerprintNs={} totalParseNs={} loadFailed={}", snapshot);
        }
    }

    private static final class Task implements Runnable {
        private Session session;
        private Runnable command;
        private PreparationBudget.Reservation reservation;

        private Task(Session session, Runnable command, PreparationBudget.Reservation reservation) {
            this.session = session;
            this.command = command;
            this.reservation = reservation;
        }

        @Override public void run() {
            Session owner = session;
            Session previous = CURRENT.get();
            CURRENT.set(owner);
            try {
                command.run();
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
                release();
            }
        }

        synchronized void release() {
            if (session == null) return;
            Session owner = session;
            session = null;
            command = null;
            reservation.close();
            reservation = null;
            owner.released();
        }
    }
    /** Delegates precisely the parser's reads; matching consumed prefixes are not whole-resource equality proof. */
    static final class ObservedReader extends Reader {
        private final Reader original;
        private final MessageDigest digest;
        private long chars;
        private long readNanos;
        private long hashingNanos;
        private boolean eof;
        private boolean fingerprintValid = true;

        ObservedReader(Reader original) {
            this.original = original;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("Required SHA-256 unavailable", exception);
            }
        }

        @Override public int read(char[] buffer, int offset, int length) throws IOException {
            long start = System.nanoTime();
            int count;
            try { count = original.read(buffer, offset, length); }
            finally { readNanos += System.nanoTime() - start; }
            if (count < 0) eof = true;
            if (count > 0) {
                start = System.nanoTime();
                chars += count;
                for (int i = offset; i < offset + count; i++) update(buffer[i]);
                hashingNanos += System.nanoTime() - start;
            }
            return count;
        }

        @Override public int read() throws IOException {
            long start = System.nanoTime();
            int value;
            try { value = original.read(); }
            finally { readNanos += System.nanoTime() - start; }
            if (value < 0) eof = true;
            else {
                start = System.nanoTime();
                chars++;
                update((char) value);
                hashingNanos += System.nanoTime() - start;
            }
            return value;
        }

        private void update(char value) {
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
        }

        @Override public long skip(long count) throws IOException { fingerprintValid = false; return original.skip(count); }
        @Override public boolean ready() throws IOException { return original.ready(); }
        @Override public boolean markSupported() { return original.markSupported(); }
        @Override public void mark(int readAheadLimit) throws IOException { original.mark(readAheadLimit); }
        @Override public void reset() throws IOException { fingerprintValid = false; original.reset(); }
        @Override public void close() throws IOException { original.close(); }
    }

    private ModelSourceDiagnostics() {}
}
