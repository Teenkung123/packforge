package com.teenkung.packforge.loader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadTraceTest {
    @AfterEach
    void reset() {
        System.clearProperty("packforge.reloadTrace");
        ReloadTrace.resetForTesting();
    }

    @Test
    void disabledTraceDoesNotRetainReloadState() {
        Object reload = new Object();
        ReloadTrace.started(1L, List.of(new ReloadTrace.PackIdentity("test", Object.class.getName())));
        ReloadTrace.created(1L, reload);
        ReloadTrace.overlay(new Object(), reload, -1L, false, 0.5F);
        ReloadTrace.completed(1L, reload, null);
        assertEquals(0, ReloadTrace.trackedReloadsForTesting());
    }

    @Test
    void traceAssociatesOverlayAndRecordsOnlyStateTransitions() {
        System.setProperty("packforge.reloadTrace", "true");
        Object reload = new Object();
        Object overlay = new Object();
        ReloadTrace.started(7L, List.of(new ReloadTrace.PackIdentity("first", String.class.getName())));
        ReloadTrace.created(7L, reload);
        ReloadTrace.overlay(overlay, reload, -1L, false, 0.0F);
        assertTrue(ReloadTrace.overlayStateForTesting(reload, overlay).contains("progress=starting"));
        ReloadTrace.overlay(overlay, reload, -1L, false, 0.5F);
        assertTrue(ReloadTrace.overlayStateForTesting(reload, overlay).contains("progress=loading"));
        ReloadTrace.overlay(overlay, reload, -1L, false, 0.6F);
        assertTrue(ReloadTrace.overlayStateForTesting(reload, overlay).contains("progress=loading"));
        ReloadTrace.overlay(overlay, reload, 10L, true, 1.0F);
        assertTrue(ReloadTrace.overlayStateForTesting(reload, overlay).contains("progress=done"));
    }

    @Test
    void creationRequiresTheStartedSession() {
        System.setProperty("packforge.reloadTrace", "true");
        ReloadTrace.created(9L, new Object());
        assertEquals(0, ReloadTrace.trackedReloadsForTesting());
    }

    @Test
    void observesAnOverlayWhoseReloadWasWrappedByAnotherMod() {
        System.setProperty("packforge.reloadTrace", "true");
        Object wrapper = new Object();
        Object overlay = new Object();
        ReloadTrace.overlay(overlay, wrapper, -1L, false, 0.5F);
        assertEquals(1, ReloadTrace.trackedReloadsForTesting());
        assertTrue(ReloadTrace.overlayStateForTesting(wrapper, overlay).contains("progress=loading"));
        ReloadTrace.overlay(overlay, wrapper, 10L, true, 1.0F);
        assertTrue(ReloadTrace.overlayStateForTesting(wrapper, overlay).contains("progress=done"));
    }

    @Test
    void overlappingReloadsTrackTheExactReloadIdentity() {
        System.setProperty("packforge.reloadTrace", "true");
        Object first = new EqualReload();
        Object second = new EqualReload();
        ReloadTrace.started(1L, List.of());
        ReloadTrace.started(2L, List.of());
        ReloadTrace.created(1L, first);
        ReloadTrace.created(2L, second);
        ReloadTrace.completed(1L, first, null);
        assertTrue(ReloadTrace.completedForTesting(first));
        assertFalse(ReloadTrace.completedForTesting(second));
        ReloadTrace.completed(2L, second, new IllegalStateException());
        assertTrue(ReloadTrace.completedForTesting(second));
    }

    private static final class EqualReload {
        @Override public boolean equals(Object other) { return other instanceof EqualReload; }
        @Override public int hashCode() { return 1; }
    }
}
