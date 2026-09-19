package dev.packbench.observer;

import dev.packbench.observer.mixin.KeyboardProbeInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.server.packs.PackResources;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

/** Standalone measurement controller. No production-mod hooks or dependencies. */
public final class BenchmarkObserver {
    private static final boolean ENABLED = Boolean.getBoolean("packforge.benchmark.enabled");
    private static final String SESSION = System.getProperty("packforge.benchmark.sessionId", "");
    private static final int RELOADS = ENABLED
        ? Integer.parseInt(System.getProperty("packforge.benchmark.reloads", "6")) : 6;
    private static final int EXPECTED_WIDTH = ENABLED
        ? Integer.parseInt(System.getProperty("packforge.benchmark.expectedWidth", "1280")) : 1280;
    private static final int EXPECTED_HEIGHT = ENABLED
        ? Integer.parseInt(System.getProperty("packforge.benchmark.expectedHeight", "720")) : 720;
    private static final String EXPECTED_PACK = System.getProperty("packforge.benchmark.expectedPackId");
    private static final String SCENARIO = System.getProperty("packforge.benchmark.scenario", "menu");
    private static final boolean DISCOVERY_ONLY = Boolean.getBoolean("packforge.benchmark.discoveryOnly");
    private static final GenerationTracker GENERATIONS = new GenerationTracker();
    private static final CharacterEvent INPUT_PROBE = new CharacterEvent(0);
    private static final ConcurrentHashMap<Long, Integer> GENERATION_INDICES = new ConcurrentHashMap<>();
    private static final ExecutorService WRITER = ENABLED ? Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "benchmark-observer-output");
        thread.setDaemon(true);
        return thread;
    }) : null;
    private static BufferedWriter output;
    private static volatile Throwable outputFailure;
    private static volatile boolean reloadComplete;
    private static volatile Throwable reloadFailure;
    private static volatile boolean windowActive;
    private static volatile boolean focusAcquired;
    private static volatile boolean focusLost;
    private static volatile Dimensions framebuffer = new Dimensions(-1, -1);
    private static volatile boolean resized;
    private static boolean initialized;
    private static boolean outputInitialized;
    private static boolean ready;
    private static boolean scenarioReady;
    private static BenchmarkScene scene;
    private static volatile boolean stopped;
    private static boolean pending;
    private static boolean allReloadsMeasured;
    private static boolean probingInput;
    private static boolean probeChangedActivity;
    private static volatile boolean overlayPresent;
    private static volatile boolean titleFading;
    private static int inputProbeAttempts;
    private static long probeGeneration;
    private static long reportedRenderedGeneration;
    private static long reportedInputGeneration;
    private static long reportedOverlayClearGeneration;
    private static int index = -1;
    private static long nextAction;
    private static long focusDeadline;

    private BenchmarkObserver() {}

    public static long resourceReloadStarted() {
        if (!ENABLED || stopped) return 0;
        initializeOutput();
        long generation = GENERATIONS.reloadStarted();
        GENERATION_INDICES.put(generation, index);
        event("resource_reload_started", index, null, null, generation);
        return generation;
    }

    public static void resourceReloadCompleted(long generation, Throwable failure) {
        if (!ENABLED || stopped || generation == 0) return;
        Integer eventIndex = GENERATION_INDICES.remove(generation);
        GENERATIONS.reloadCompleted(generation, failure == null);
        event(failure == null ? "resource_reload_complete" : "resource_reload_failed", eventIndex == null ? index : eventIndex,
            failure == null ? null : failure.getClass().getName(), null, generation);
    }

    public static void inputTick() { if (ENABLED && !stopped) GENERATIONS.inputTick(); }
    public static void extractionStarted() { if (ENABLED && !stopped) GENERATIONS.extractionStarted(); }
    public static void worldRendered() { if (ENABLED && !stopped) GENERATIONS.worldRendered(); }
    public static void menuRendered() { if (ENABLED && !stopped) GENERATIONS.menuRendered(); }
    public static void renderCompleted() { if (ENABLED && !stopped) GENERATIONS.renderCompleted(); }
    public static void worldInputDispatched() { if (ENABLED && !stopped) GENERATIONS.inputDispatched(true); }

    public static void menuInputDispatched(CharacterEvent event) {
        if (ENABLED && !stopped && probingInput && event == INPUT_PROBE) GENERATIONS.inputDispatched(false);
    }

    public static void inputActivityObserved() {
        if (ENABLED && probingInput) probeChangedActivity = true;
    }

    private static synchronized void initializeOutput() {
        if (outputInitialized) return;
        String destination = System.getProperty("packforge.benchmark.output", "");
        if (SESSION.isBlank() || destination.isBlank() || RELOADS < 1 || RELOADS > 128) {
            throw new IllegalArgumentException("Benchmark requires sessionId, output, and reloads in 1..128");
        }
        if (EXPECTED_WIDTH < 1 || EXPECTED_HEIGHT < 1 || (EXPECTED_PACK != null && EXPECTED_PACK.isBlank())) {
            throw new IllegalArgumentException("Benchmark requires positive expected dimensions and a nonblank expectedPackId when provided");
        }
        if (!SCENARIO.equals("menu") && !SCENARIO.equals("inworld")) {
            throw new IllegalArgumentException("Benchmark scenario must be menu or inworld");
        }
        Path path = Path.of(destination).toAbsolutePath();
        outputInitialized = true;
        WRITER.execute(() -> {
            try {
                Files.createDirectories(path.getParent());
                output = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException | RuntimeException error) {
                outputFailure = error;
            }
        });
    }

    public static void afterFrame(Minecraft minecraft) {
        if (!ENABLED || stopped) return;
        framebuffer = new Dimensions(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        overlayPresent = ClientReadiness.overlayPresent(minecraft);
        titleFading = ClientReadiness.titleFading(minecraft);
        if (!initialized) {
            initialized = true;
            initializeOutput();
            focusDeadline = System.nanoTime() + 1_000_000_000L;
            GLFW.glfwFocusWindow(minecraft.getWindow().handle());
        }
        if (outputFailure != null) {
            stopped = true;
            WRITER.execute(() -> {
                try {
                    if (output != null) output.close();
                } catch (IOException closeFailure) {
                    outputFailure.addSuppressed(closeFailure);
                }
            });
            WRITER.shutdown();
            throw new IllegalStateException("Benchmark event output failed", outputFailure);
        }
        if (reloadFailure != null) {
            fail(minecraft, "reload failed", reloadFailure);
            return;
        }
        if (GENERATIONS.snapshot().failure() != null) {
            finish(minecraft, "error", GENERATIONS.snapshot().failure());
            return;
        }
        if (!DISCOVERY_ONLY && (resized || (ready && !framebuffer.matchesExpected()))) {
            finish(minecraft, "error", "framebuffer dimensions changed during benchmark");
            return;
        }
        windowActive = GLFW.glfwGetWindowAttrib(minecraft.getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (windowActive) focusAcquired = true;
        if (focusLost || (!windowActive && focusAcquired)) {
            finish(minecraft, "error", "window focus lost during benchmark");
            return;
        }
        if (!windowActive) {
            if (System.nanoTime() >= focusDeadline || ClientReadiness.ready(minecraft)) {
                finish(minecraft, "error", "initial window focus request did not acquire focus");
            }
            return;
        }
        String throttleReason = minecraft.getFramerateLimitTracker().getThrottleReason().name();
        if (throttleReason.equals("SHORT_AFK") || throttleReason.equals("LONG_AFK")
            || throttleReason.equals("WINDOW_ICONIFIED")) {
            finish(minecraft, "error", "background throttle active: " + throttleReason);
            return;
        }
        GenerationTracker.Snapshot snapshot = GENERATIONS.snapshot();
        if (!overlayPresent && snapshot.generation() > 0 && snapshot.activeReloads() == 0
            && reportedOverlayClearGeneration != snapshot.generation()) {
            reportedOverlayClearGeneration = snapshot.generation();
            event("overlay_cleared", index, null);
            if (ready && !pending) nextAction = System.nanoTime() + 1_000_000_000L;
        }
        if (allReloadsMeasured) {
            if (!overlayPresent) finish(minecraft, "complete", null);
            return;
        }
        if (scene != null && !scenarioReady) {
            try {
                if (!scene.poll(minecraft)) return;
            } catch (RuntimeException failure) {
                fail(minecraft, "scene setup failed", failure);
                return;
            }
        }
        boolean inWorld = ready && SCENARIO.equals("inworld");
        if (GENERATIONS.renderedFrame(inWorld) && reportedRenderedGeneration != snapshot.generation()) {
            reportedRenderedGeneration = snapshot.generation();
            event("resources_rendered", index, null);
        }
        if (!(inWorld ? ClientReadiness.worldReady(minecraft) : ClientReadiness.ready(minecraft))) return;
        if (!GENERATIONS.usableFrame(inWorld)) return;
        if (!DISCOVERY_ONLY && !framebuffer.matchesExpected()) {
            finish(minecraft, "error", "framebuffer dimensions do not match expected dimensions");
            return;
        }
        if (!inWorld && !GENERATIONS.inputReady(false)) {
            if (probeGeneration != snapshot.generation()) {
                probeGeneration = snapshot.generation();
                inputProbeAttempts = 0;
            }
            ++inputProbeAttempts;
            // The ordinary character handler includes window, screen, and transformed
            // overlay gates. NUL is not chat text and invokes no key binding. Unlike
            // keyPress/onScroll, this path does not reset vanilla's idle timer.
            probingInput = true;
            try {
                ((KeyboardProbeInvoker) minecraft.keyboardHandler).benchmark$dispatchCharacter(
                    minecraft.getWindow().handle(), INPUT_PROBE);
            } finally {
                probingInput = false;
            }
            if (probeChangedActivity) {
                finish(minecraft, "error", "input probe changed activity tracking");
                return;
            }
        }
        if (!GENERATIONS.inputReady(inWorld)) return;
        if (reportedInputGeneration != snapshot.generation()) {
            reportedInputGeneration = snapshot.generation();
            event("input_ready", index, null);
        }

        if (!ready) {
            List<String> packs = activePacks(minecraft);
            if (packs == null) return;
            ready = true;
            event("ready", index, null, packs);
            if (DISCOVERY_ONLY) {
                finish(minecraft, "discovery_complete", "untimed framebuffer discovery; never a qualifying process");
                return;
            }
            nextAction = System.nanoTime() + 1_000_000_000L;
            if (SCENARIO.equals("inworld")) {
                event("world_loading", index, null, packs);
                try {
                    scene = new BenchmarkScene(minecraft);
                    minecraft.execute(() -> scene.create(minecraft));
                } catch (RuntimeException failure) {
                    fail(minecraft, "scene setup failed", failure);
                }
                return;
            }
        }
        if (!scenarioReady) {
            scenarioReady = true;
            GENERATIONS.arm();
            if (inWorld) {
                event("world_ready", index, null, activePacks(minecraft));
                nextAction = System.nanoTime() + 1_000_000_000L;
            }
        }
        if (pending && reloadComplete) {
            if (inWorld && !scene.verifyCurrent(minecraft)) {
                finish(minecraft, "error", "benchmark scene entities, sign, or camera changed");
                return;
            }
            List<String> packs = activePacks(minecraft);
            if (packs == null) return;
            pending = false;
            reloadComplete = false;
            GENERATIONS.requestFinished();
            if (GENERATIONS.snapshot().failure() != null) {
                finish(minecraft, "error", GENERATIONS.snapshot().failure());
                return;
            }
            event("frame_ready", index, null, packs);
            if (index == RELOADS - 1) {
                allReloadsMeasured = true;
                if (!overlayPresent) finish(minecraft, "complete", null);
                return;
            }
            nextAction = System.nanoTime() + 1_000_000_000L;
        }
        if (!pending && !overlayPresent && System.nanoTime() >= nextAction) {
            pending = true;
            final int requestIndex = ++index;
            minecraft.execute(() -> {
                GENERATIONS.requestStarted();
                event("reload_requested", requestIndex, null);
                try {
                    minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
                        if (stopped) return;
                        if (failure != null) reloadFailure = failure;
                        else {
                            event("reload_complete", requestIndex, null);
                            reloadComplete = true;
                        }
                    });
                } catch (RuntimeException failure) {
                    reloadFailure = failure;
                }
            });
        }
    }

    public static void onFramebufferSize(int width, int height) {
        if (!ENABLED) return;
        framebuffer = new Dimensions(width, height);
        if (ready && !framebuffer.matchesExpected()) resized = true;
    }

    private static List<String> activePacks(Minecraft minecraft) {
        final List<String> packs;
        try (var stream = minecraft.getResourceManager().listPacks()) {
            packs = stream.map(PackResources::packId).toList();
        } catch (RuntimeException failure) {
            finish(minecraft, "error", "could not observe active resource packs: " + failure.getClass().getName());
            return null;
        }
        if (EXPECTED_PACK != null && !packs.contains(EXPECTED_PACK)) {
            stopped = true;
            event("error", index, "expected resource pack is not active", packs);
            closeAndStop(minecraft);
            return null;
        }
        return packs;
    }

    /** Records even a focus loss/regain between rendered frames; never replaces GLFW callbacks. */
    public static void onWindowFocus(boolean focused) {
        if (!ENABLED || !initialized) return;
        windowActive = focused;
        if (focused) focusAcquired = true;
        else if (focusAcquired) focusLost = true;
    }

    private static void finish(Minecraft minecraft, String event, String reason) {
        stopped = true;
        event(event, index, reason);
        closeAndStop(minecraft);
    }

    private static void fail(Minecraft minecraft, String context, Throwable failure) {
        // Failed attempts are never headline samples. Retain the complete cause
        // chain (including the exact scene command) in the runner's stderr log.
        System.err.println("[Packbench] " + context);
        failure.printStackTrace(System.err);
        Throwable cause = failure;
        for (int depth = 0; depth < 16 && cause.getCause() != null && cause.getCause() != cause; depth++) {
            cause = cause.getCause();
        }
        finish(minecraft, "error", context + ": " + cause.getClass().getName() + ": " + cause.getMessage());
    }

    private static void closeAndStop(Minecraft minecraft) {
        WRITER.execute(() -> {
            try {
                if (output != null) output.close();
            } catch (IOException error) {
                outputFailure = error;
            } finally {
                minecraft.execute(() -> {
                    if (outputFailure != null) throw new IllegalStateException("Benchmark output failed", outputFailure);
                    minecraft.stop();
                });
            }
        });
        WRITER.shutdown();
    }

    private static void event(String event, int eventIndex, String reason) {
        event(event, eventIndex, reason, null);
    }

    private static void event(String event, int eventIndex, String reason, List<String> packs) {
        event(event, eventIndex, reason, packs, GENERATIONS.snapshot().generation());
    }

    private static void event(String event, int eventIndex, String reason, List<String> packs, long eventGeneration) {
        // Capture clocks on the observing thread, before queueing any filesystem work.
        long nanos = System.nanoTime();
        long epoch = System.currentTimeMillis();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        Dimensions dimensions = framebuffer;
        GenerationTracker.Snapshot generation = GENERATIONS.snapshot();
        String record = "{\"schema\":1,\"sessionId\":" + quote(SESSION) + ",\"event\":" + quote(event)
            + ",\"observerProtocol\":3,\"scenario\":" + quote(SCENARIO)
            + ",\"discoveryOnly\":" + DISCOVERY_ONLY
            + (SCENARIO.equals("inworld") ? ",\"sceneId\":" + quote(BenchmarkScene.WORLD_ID) + ",\"sceneSeed\":" + BenchmarkScene.SEED : "")
            + (SCENARIO.equals("inworld") ? ",\"sceneImplementationSha256\":" + quote(SceneIdentity.IMPLEMENTATION_SHA256)
                + ",\"sceneSourceSha256\":" + quote(SceneIdentity.SOURCE_SHA256)
                + ",\"sceneEntityUuids\":[" + String.join(",", SceneIdentity.ENTITY_UUIDS.stream().map(value -> quote(value.toString())).toList()) + "]"
                + ",\"sceneObservedEntityUuids\":[" + (scene == null ? "" : String.join(",", scene.observedEntityUuids().stream().map(BenchmarkObserver::quote).toList())) + "]" : "")
            + ",\"resourceGeneration\":" + eventGeneration
            + ",\"renderedResourceGeneration\":" + generation.renderedGeneration()
            + ",\"renderSequence\":" + generation.renderSequence() + ",\"inputTickSequence\":" + generation.inputTickSequence()
            + ",\"resourceReloadCount\":" + generation.generation() + ",\"activeResourceReloads\":" + generation.activeReloads()
            + ",\"requestResourceGenerations\":" + generation.requestGenerations() + ",\"worldRendered\":" + generation.worldRendered()
            + ",\"menuRendered\":" + generation.menuRendered()
            + ",\"menuInputDispatchGeneration\":" + generation.menuInputDispatchGeneration()
            + ",\"worldInputDispatchGeneration\":" + generation.worldInputDispatchGeneration()
            + ",\"overlayPresent\":" + overlayPresent + ",\"titleFading\":" + titleFading
            + ",\"inputProbeAttempts\":" + inputProbeAttempts + ",\"inputProbeChangedActivity\":" + probeChangedActivity
            + ",\"inputProbeGeneration\":" + probeGeneration
            + ",\"epochMillis\":" + epoch + ",\"nanoTime\":" + nanos + ",\"jvmUptimeMillis\":" + uptime
            + ",\"reloadIndex\":" + eventIndex + ",\"windowActive\":" + windowActive
            + ",\"framebufferWidth\":" + dimensions.width() + ",\"framebufferHeight\":" + dimensions.height()
            + (packs == null ? "" : ",\"activePackIds\":[" + String.join(",", packs.stream().map(BenchmarkObserver::quote).toList()) + "]")
            + (reason == null ? "" : ",\"reason\":" + quote(reason)) + "}";
        WRITER.execute(() -> {
            if (outputFailure != null) return;
            try {
                output.write(record);
                output.newLine();
                output.flush();
            } catch (IOException error) {
                outputFailure = error;
            }
        });
    }

    private record Dimensions(int width, int height) {
        boolean matchesExpected() { return width == EXPECTED_WIDTH && height == EXPECTED_HEIGHT; }
    }

    static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' || character == '\\') result.append('\\').append(character);
            else if (character < 32) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }
}
