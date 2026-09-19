package com.teenkung.packforge.client.model;

import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSourceDiagnosticsTest {
    @Test
    void cancellationKeepsOriginalFutureAndChargesQueuedOwnersUntilTheyFinish() {
        ReloadExecutionContext context = ReloadExecutionContext.startForTesting(ReloadFeatureSnapshot.capture());
        try {
            var session = new ModelSourceDiagnostics.Session(context, context.preparationBudget().tryReserve(1024));
            var queued = new ArrayList<Runnable>();
            session.submit(queued::add, () -> {});
            CompletableFuture<Void> original = new CompletableFuture<>();
            assertSame(original, ModelSourceDiagnostics.attach(session, original));
            original.cancel(false);
            assertTrue(original.isCancelled());
            assertEquals(1280, context.preparationBudget().used());
            queued.removeFirst().run();
            assertEquals(0, context.preparationBudget().used());
        } finally {
            ReloadExecutionContext.finish(context);
        }
    }

    @Test
    void rejectedAndInlineFailingTasksReleaseTheirOwnershipExactlyOnce() {
        ReloadExecutionContext context = ReloadExecutionContext.startForTesting(ReloadFeatureSnapshot.capture());
        try {
            var session = new ModelSourceDiagnostics.Session(context, context.preparationBudget().tryReserve(1024));
            assertThrows(RejectedExecutionException.class, () -> session.submit(command -> {
                throw new RejectedExecutionException("test");
            }, () -> {}));
            assertEquals(1024, context.preparationBudget().used());
            assertThrows(IllegalStateException.class, () -> session.submit(Runnable::run, () -> {
                throw new IllegalStateException("original task");
            }));
            assertEquals(1024, context.preparationBudget().used());
            session.finish(null);
            assertEquals(0, context.preparationBudget().used());
        } finally {
            ReloadExecutionContext.finish(context);
        }
    }

    @Test
    void doesNotReadAheadOrCloseOriginalAfterPartialConsumption() throws Exception {
        TrackingReader source = new TrackingReader("{} trailing data that vanilla may never consume");
        var observed = new ModelSourceDiagnostics.ObservedReader(source);
        assertEquals('{', observed.read());
        assertEquals(1, source.readCalls);
        assertEquals(1, source.characters);
        assertFalse(source.closed);
        observed.close();
        assertTrue(source.closed);
    }

    @Test
    void forwardsBulkReadBoundsAndOriginalFailure() throws Exception {
        TrackingReader source = new TrackingReader("abcdef");
        var observed = new ModelSourceDiagnostics.ObservedReader(source);
        char[] data = {'x', 'x', 'x', 'x'};
        assertEquals(2, observed.read(data, 1, 2));
        assertEquals("xabx", new String(data));
        assertEquals(1, source.readCalls);
        IOException expected = new IOException("original read failure");
        Reader failing = new Reader() {
            @Override public int read(char[] buffer, int offset, int length) throws IOException { throw expected; }
            @Override public void close() {}
        };
        var failedObserver = new ModelSourceDiagnostics.ObservedReader(failing);
        assertSame(expected, assertThrows(IOException.class, () -> failedObserver.read(data, 0, 1)));
    }

    @Test
    void preservesMarkResetSkipAndReadiness() throws Exception {
        var observed = new ModelSourceDiagnostics.ObservedReader(new StringReader("abc"));
        assertTrue(observed.markSupported());
        assertTrue(observed.ready());
        observed.mark(3);
        assertEquals('a', observed.read());
        observed.reset();
        assertEquals(2, observed.skip(2));
        assertEquals('c', observed.read());
        assertEquals(-1, observed.read());
    }

    private static final class TrackingReader extends Reader {
        private final StringReader delegate;
        private int readCalls;
        private int characters;
        private boolean closed;

        private TrackingReader(String content) { delegate = new StringReader(content); }
        @Override public int read(char[] buffer, int offset, int length) throws IOException {
            readCalls++;
            int result = delegate.read(buffer, offset, length);
            characters += Math.max(0, result);
            return result;
        }
        @Override public void close() { closed = true; delegate.close(); }
    }
}
