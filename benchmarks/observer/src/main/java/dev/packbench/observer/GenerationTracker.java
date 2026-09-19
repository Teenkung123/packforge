package dev.packbench.observer;

import java.util.HashSet;
import java.util.Set;

/** Pure observation state; deliberately independent of Minecraft and optimizer code. */
public final class GenerationTracker {
    private final Set<Long> active = new HashSet<>();
    private long generation;
    private long completedGeneration;
    private long extractingGeneration;
    private long renderedGeneration;
    private long tickGeneration;
    private long renderSequence;
    private long inputTickSequence;
    private long menuInputDispatchGeneration;
    private long worldInputDispatchGeneration;
    private boolean worldRendered;
    private boolean menuRendered;
    private boolean armed;
    private boolean requested;
    private int requestGenerations;
    private String failure;

    public synchronized long reloadStarted() {
        long id = ++generation;
        active.add(id);
        if (armed && !requested) fail("unrequested resource reload after scenario readiness");
        if (requested && ++requestGenerations != 1) fail("multiple resource generations for one requested reload");
        return id;
    }

    public synchronized void reloadCompleted(long id, boolean successful) {
        if (!active.remove(id)) fail("resource generation completed more than once");
        if (!successful) fail("resource generation failed");
        else if (id == generation) completedGeneration = id;
    }

    public synchronized void arm() { armed = true; }

    public synchronized void requestStarted() {
        if (requested || !active.isEmpty()) fail("requested reload overlaps unfinished resource work");
        requested = true;
        requestGenerations = 0;
    }

    public synchronized void requestFinished() {
        if (!requested || requestGenerations != 1) fail("requested reload did not produce exactly one resource generation");
        requested = false;
    }

    public synchronized void inputTick() {
        ++inputTickSequence;
        tickGeneration = settledGeneration();
    }

    public synchronized void extractionStarted() {
        extractingGeneration = settledGeneration();
        renderedGeneration = 0;
        worldRendered = false;
        menuRendered = false;
    }

    public synchronized void worldRendered() { worldRendered = true; }
    public synchronized void menuRendered() { menuRendered = true; }

    public synchronized void inputDispatched(boolean world) {
        long settled = settledGeneration();
        if (settled != 0 && settled == renderedGeneration && (world ? worldRendered : menuRendered)) {
            if (world) worldInputDispatchGeneration = settled;
            else menuInputDispatchGeneration = settled;
        }
    }

    public synchronized void renderCompleted() {
        ++renderSequence;
        renderedGeneration = extractingGeneration != 0 && extractingGeneration == settledGeneration()
            ? extractingGeneration : 0;
    }

    public synchronized boolean usableFrame(boolean requireWorld) {
        return renderedFrame(requireWorld) && settledGeneration() == tickGeneration;
    }

    public synchronized boolean renderedFrame(boolean requireWorld) {
        long settled = settledGeneration();
        return failure == null && settled != 0 && settled == renderedGeneration
            && (requireWorld ? worldRendered : menuRendered);
    }

    public synchronized boolean inputReady(boolean world) {
        return settledGeneration() != 0 && settledGeneration()
            == (world ? worldInputDispatchGeneration : menuInputDispatchGeneration);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(generation, renderedGeneration, renderSequence, inputTickSequence,
            active.size(), requestGenerations, worldRendered, menuRendered,
            menuInputDispatchGeneration, worldInputDispatchGeneration, failure);
    }

    private long settledGeneration() {
        return active.isEmpty() && completedGeneration == generation ? completedGeneration : 0;
    }

    private void fail(String reason) { if (failure == null) failure = reason; }

    public record Snapshot(long generation, long renderedGeneration, long renderSequence,
                           long inputTickSequence, int activeReloads, int requestGenerations,
                           boolean worldRendered, boolean menuRendered, long menuInputDispatchGeneration,
                           long worldInputDispatchGeneration, String failure) {}
}
