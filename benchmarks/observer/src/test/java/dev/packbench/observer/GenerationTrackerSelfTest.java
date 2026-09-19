package dev.packbench.observer;

/** Runs with only a JDK; no optimizer, Minecraft, graphics, or test library. */
public final class GenerationTrackerSelfTest {
    private static int assertions;

    public static void main(String[] args) {
        GenerationTracker state = new GenerationTracker();
        check(!state.usableFrame(false), "no initial generation");
        long startup = state.reloadStarted();
        state.extractionStarted();
        state.inputTick();
        state.renderCompleted();
        check(!state.usableFrame(false), "hidden overlay cannot qualify active startup");
        state.reloadCompleted(startup, true);
        state.inputTick();
        check(!state.usableFrame(false), "frame extracted before completion is stale");
        state.extractionStarted();
        state.menuRendered();
        state.renderCompleted();
        check(state.usableFrame(false), "fresh menu frame");
        check(!state.inputReady(false), "render alone is not input acknowledgment");
        state.inputDispatched(true);
        check(!state.inputReady(false), "world input cannot acknowledge menu input");
        state.inputDispatched(false);
        check(state.inputReady(false), "menu dispatcher acknowledged current rendered generation");
        check(!state.usableFrame(true), "menu frame cannot qualify world");
        state.extractionStarted();
        state.worldRendered();
        state.renderCompleted();
        check(state.usableFrame(true), "fresh world frame");
        check(!state.inputReady(true), "menu input cannot acknowledge world input");
        state.inputDispatched(true);
        check(state.inputReady(true), "world input dispatcher acknowledged world");
        state.arm();
        state.requestStarted();
        long reload = state.reloadStarted();
        check(!state.inputReady(false) && !state.inputReady(true), "input acknowledgments cannot cross generations");
        check(!state.usableFrame(true), "old world frame cannot qualify new generation");
        state.reloadCompleted(reload, true);
        state.extractionStarted();
        state.worldRendered();
        state.renderCompleted();
        check(!state.usableFrame(true), "must process usable input after completion");
        state.inputTick();
        check(state.usableFrame(true), "new resources, input, and rendered world");
        state.requestFinished();
        check(state.snapshot().failure() == null, "single generation accepted");

        GenerationTracker overlap = new GenerationTracker();
        overlap.arm();
        overlap.requestStarted();
        long first = overlap.reloadStarted();
        long second = overlap.reloadStarted();
        overlap.reloadCompleted(second, true);
        overlap.inputTick();
        overlap.extractionStarted();
        overlap.renderCompleted();
        check(!overlap.usableFrame(false), "latest completion does not hide older active generation");
        overlap.reloadCompleted(first, true);
        check(overlap.snapshot().failure().contains("multiple"), "extra generations reject request");

        GenerationTracker midFrame = new GenerationTracker();
        long old = midFrame.reloadStarted();
        midFrame.reloadCompleted(old, true);
        midFrame.inputTick();
        midFrame.extractionStarted();
        long next = midFrame.reloadStarted();
        midFrame.reloadCompleted(next, true);
        midFrame.inputTick();
        midFrame.renderCompleted();
        check(!midFrame.usableFrame(false), "reload completing during frame invalidates extracted old state");
        midFrame.inputDispatched(false);
        check(!midFrame.inputReady(false), "stale rendered frame cannot acknowledge input");

        GenerationTracker failed = new GenerationTracker();
        long failure = failed.reloadStarted();
        failed.reloadCompleted(failure, false);
        failed.inputTick();
        failed.extractionStarted();
        failed.renderCompleted();
        check(!failed.usableFrame(false), "failure cannot produce readiness");

        GenerationTracker missing = new GenerationTracker();
        missing.requestStarted();
        missing.requestFinished();
        check(missing.snapshot().failure() != null, "no-op requested future cannot qualify");

        GenerationTracker unsolicited = new GenerationTracker();
        unsolicited.arm();
        unsolicited.reloadStarted();
        check(unsolicited.snapshot().failure() != null, "unsolicited reload rejects after readiness");
        System.out.println("GenerationTrackerSelfTest: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        ++assertions;
    }
}
