package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Opt-in reload diagnostics. Kept separate from normal reload accounting. */
public final class ReloadTrace {
    private static final String PROPERTY = "packforge.reloadTrace";
    private static final int MAX_SESSIONS = 64;
    private static final int MAX_OVERLAYS = 8;
    private static final Object LOCK = new Object();
    private static final ReferenceQueue<Object> RELOADS = new ReferenceQueue<>();
    private static final Map<IdentityReference, Session> SESSIONS = new HashMap<>();
    private static final Map<Long, Session> STARTED = new HashMap<>();

    private ReloadTrace() {}

    public static void started(long reloadId, List<PackIdentity> packs) {
        if (!enabled()) return;
        Session session = new Session(reloadId, packs == null ? List.of() : List.copyOf(packs));
        synchronized (LOCK) {
            if (STARTED.size() >= MAX_SESSIONS) STARTED.remove(STARTED.keySet().iterator().next());
            STARTED.put(reloadId, session);
        }
        PackForge.LOGGER.info("PackForge reload trace: id={} initiatingPath={} packs={}", reloadId, initiatingPath(), session.packs);
    }

    public static void created(long reloadId, Object reload) {
        if (!enabled() || reload == null) return;
        Session session;
        synchronized (LOCK) {
            cleanReloads();
            session = STARTED.remove(reloadId);
            if (session == null) {
                PackForge.LOGGER.info("PackForge reload trace: id={} reload={} createdWithoutStart", reloadId, identity(reload));
                return;
            }
            if (SESSIONS.size() >= MAX_SESSIONS) SESSIONS.remove(SESSIONS.keySet().iterator().next());
            SESSIONS.put(new IdentityReference(reload, RELOADS), session);
        }
        PackForge.LOGGER.info("PackForge reload trace: id={} reload={} created", reloadId, identity(reload));
    }

    public static void failedCreation(long reloadId, Throwable error) {
        if (!enabled()) return;
        synchronized (LOCK) { STARTED.remove(reloadId); }
        PackForge.LOGGER.info("PackForge reload trace: id={} creation={}", reloadId, error.getClass().getName());
    }

    public static void completed(long reloadId, Object reload, Throwable error) {
        if (!enabled() || reload == null) return;
        Session session = session(reload);
        if (session == null) return;
        synchronized (session) {
            if (session.completed) return;
            session.completed = true;
        }
        PackForge.LOGGER.info("PackForge reload trace: id={} reload={} completion={}", reloadId,
                identity(reload), error == null ? "success" : error.getClass().getName());
    }

    /** Called from the independent overlay observer; logs only state transitions. */
    public static void overlay(Object overlay, Object reload, long fadeOutStart, boolean done, float progress) {
        if (!enabled() || overlay == null || reload == null) return;
        Session session = overlaySession(reload);
        ReloadExecutionContext context = ReloadExecutionContext.visible();
        long visibleContextId = context == null ? -1L : context.reloadId();
        String state = "context=" + visibleContextId + " reload=" + identity(reload) + " progress=" +
                (done ? "done" : progress <= 0.0F ? "starting" : progress >= 1.0F ? "ready" : "loading") +
                " fade=" + (fadeOutStart < 0L ? "pending" : "started");
        synchronized (session) {
            session.cleanOverlays();
            IdentityReference key = new IdentityReference(overlay, session.overlayReferences);
            String previous = session.overlays.get(key);
            if (state.equals(previous)) return;
            if (previous == null && session.overlays.size() >= MAX_OVERLAYS) {
                session.overlays.remove(session.overlays.keySet().iterator().next());
            }
            session.overlays.put(key, state);
        }
        PackForge.LOGGER.info("PackForge reload trace: id={} overlay={} reload={} progressState={}", session.reloadId,
                identity(overlay), identity(reload), state);
    }

    static int trackedReloadsForTesting() {
        synchronized (LOCK) {
            cleanReloads();
            return SESSIONS.size();
        }
    }
    static String overlayStateForTesting(Object reload, Object overlay) {
        Session session = session(reload);
        if (session == null) return null;
        synchronized (session) {
            session.cleanOverlays();
            return session.overlays.get(new IdentityReference(overlay));
        }
    }
    static boolean completedForTesting(Object reload) {
        Session session = session(reload);
        if (session == null) return false;
        synchronized (session) { return session.completed; }
    }
    static void resetForTesting() {
        synchronized (LOCK) {
            SESSIONS.clear();
            STARTED.clear();
            cleanReloads();
        }
    }

    public static boolean isEnabled() { return Boolean.getBoolean(PROPERTY); }

    private static boolean enabled() { return isEnabled(); }

    private static String initiatingPath() {
        return StackWalker.getInstance().walk(frames -> {
            List<String> callers = frames
                    .filter(frame -> !frame.getClassName().equals(ReloadTrace.class.getName()))
                    .filter(frame -> !frame.getClassName().endsWith("ReloadableResourceManagerMixin"))
                    .limit(24)
                    .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                    .toList();
            return callers.isEmpty() ? "unknown" : String.join(" <- ", callers);
        });
    }

    private static String identity(Object value) {
        if (value == null) return "null";
        return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static Session session(Object reload) {
        synchronized (LOCK) {
            cleanReloads();
            return SESSIONS.get(new IdentityReference(reload));
        }
    }

    /** A companion may wrap the returned instance after our manager hook has observed it. */
    private static Session overlaySession(Object reload) {
        synchronized (LOCK) {
            cleanReloads();
            IdentityReference lookup = new IdentityReference(reload);
            Session found = SESSIONS.get(lookup);
            if (found != null) return found;
            if (SESSIONS.size() >= MAX_SESSIONS) SESSIONS.remove(SESSIONS.keySet().iterator().next());
            Session unobserved = new Session(-1L, List.of());
            SESSIONS.put(new IdentityReference(reload, RELOADS), unobserved);
            return unobserved;
        }
    }

    private static void cleanReloads() {
        for (IdentityReference reference; (reference = (IdentityReference) RELOADS.poll()) != null; ) SESSIONS.remove(reference);
    }

    public record PackIdentity(String id, String type) {}

    private static final class Session {
        private final long reloadId;
        private final List<PackIdentity> packs;
        private final ReferenceQueue<Object> overlayReferences = new ReferenceQueue<>();
        private final Map<IdentityReference, String> overlays = new HashMap<>();
        private boolean completed;

        private Session(long reloadId, List<PackIdentity> packs) {
            this.reloadId = reloadId;
            this.packs = packs;
        }

        private void cleanOverlays() {
            for (IdentityReference reference; (reference = (IdentityReference) overlayReferences.poll()) != null; ) overlays.remove(reference);
        }
    }

    private static final class IdentityReference extends WeakReference<Object> {
        private final int hash;

        private IdentityReference(Object value, ReferenceQueue<Object> queue) {
            super(value, queue);
            this.hash = System.identityHashCode(value);
        }

        private IdentityReference(Object value) {
            super(value);
            this.hash = System.identityHashCode(value);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            Object value = get();
            return value != null && other instanceof IdentityReference reference && value == reference.get();
        }
    }
}
