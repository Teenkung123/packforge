package com.teenkung.packforge.client;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.PackForgeCore;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadHooks;
import com.teenkung.packforge.loader.RuntimeSmokeScenario;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only cancellation/failure/retry controller used by representative smoke runs.
 * It is selected through {@code packforge.runtimeSmokeScenario}; ordinary launches
 * never install these hooks.
 */
public final class RuntimeSmokeScenarioController {
	private static final long ACTION_DELAY_MILLIS = 1_000L;
	private static final long CANCELLATION_DELAY_MILLIS = 250L;
	private static final long CLEANUP_POLL_DELAY_MILLIS = 250L;
	private static final int MAX_CLEANUP_POLLS = 40;
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
	private static final Object STATE_LOCK = new Object();
	private static final Set<ReloadExecutionContext> HANDLED_COMPLETIONS =
		Collections.newSetFromMap(new IdentityHashMap<>());

	private static RuntimeSmokeScenario.Scenario scenario = RuntimeSmokeScenario.Scenario.NONE;
	private static boolean enabled;
	private static boolean initialCompletionObserved;
	private static boolean pendingClientAction;
	private static boolean readinessReported;
	private static boolean failed;
	private static int scenarioAttempt;
	private static int injectedFailures;
	private static int cancellations;
	private static int recordedAttempt = -1;
	private static int cleanupPolls;
	private static boolean expectedFailureInFlight;
	private static boolean overlayCleared;

	private RuntimeSmokeScenarioController() {
	}

	/**
	 * Installs the scenario controller when the selector is present and non-repeat.
	 * Returning {@code true} tells the normal reload-count controller not to install
	 * a second controller for the same process.
	 */
	public static boolean tryInitialize() {
		if (!INITIALIZED.compareAndSet(false, true)) {
			return true;
		}

		String property;
		try {
			property = System.getProperty(RuntimeSmokeScenario.PROPERTY_NAME);
			if (property == null || property.isBlank()) {
				property = System.getenv("PACKFORGE_RUNTIME_SMOKE_SCENARIO");
			}
		} catch (SecurityException exception) {
			return false;
		}
		if (property == null || property.isBlank() || property.trim().equalsIgnoreCase("repeat")) {
			return false;
		}

		RuntimeSmokeScenario.Scenario selected = RuntimeSmokeScenario.configure(property);
		if (selected == RuntimeSmokeScenario.Scenario.NONE || selected == RuntimeSmokeScenario.Scenario.REPEAT) {
			PackForge.LOGGER.warn("PackForge runtime smoke scenario is disabled: unsupported selector '{}'", property);
			return true;
		}
		synchronized (STATE_LOCK) {
			scenario = selected;
			enabled = true;
			pendingClientAction = false;
			overlayCleared = false;
		}
		ReloadHooks.registerCompletionHook(RuntimeSmokeScenarioController::onReloadCompletion);
		HeavyFixtureEvidence.initialize();
		PackForge.LOGGER.info("PackForge runtime smoke scenario enabled: name={}", selected.id());
		return true;
	}

	private static void onReloadCompletion(ReloadExecutionContext context, Throwable error) {
		if (context == null) {
			return;
		}
		boolean scheduleAction = false;
		boolean dispatchScenarioFailure = false;
		int failedAttempt = -1;
		synchronized (STATE_LOCK) {
			if (!enabled || failed || !HANDLED_COMPLETIONS.add(context)) {
				return;
			}
			if (!initialCompletionObserved) {
				if (error != null) {
					failLocked("startup resource reload failed", error);
					return;
				}
				initialCompletionObserved = true;
				pendingClientAction = true;
				scheduleAction = true;
			} else if (error != null) {
				dispatchScenarioFailure = true;
				failedAttempt = scenarioAttempt;
			} else {
				return;
			}
		}
		if (scheduleAction) {
			scheduleDelayedClientAction();
		}
		if (dispatchScenarioFailure) {
			try {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft == null) {
					throw new IllegalStateException("Minecraft client is unavailable");
				}
				dispatchAttemptResult(minecraft, failedAttempt, null, error, new AtomicBoolean());
			} catch (Throwable exception) {
				fail("could not dispatch failed scenario reload from lifecycle hook", exception, null);
			}
		}
	}

	private static void scheduleDelayedClientAction() {
		final Minecraft minecraft;
		try {
			minecraft = Minecraft.getInstance();
			if (minecraft == null) {
				throw new IllegalStateException("Minecraft client is unavailable");
			}
			CompletableFuture.delayedExecutor(ACTION_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(
				() -> {
					try {
						minecraft.execute(RuntimeSmokeScenarioController::runPendingClientAction);
					} catch (Throwable exception) {
						fail("could not schedule runtime smoke scenario action on the Minecraft client executor", exception, minecraft);
					}
				});
		} catch (Throwable exception) {
			fail("could not schedule runtime smoke scenario action", exception, null);
		}
	}

	private static void runPendingClientAction() {
		final Minecraft minecraft;
		synchronized (STATE_LOCK) {
			if (!enabled || failed || !pendingClientAction) {
				return;
			}
			pendingClientAction = false;
		}
		try {
			minecraft = Minecraft.getInstance();
			if (minecraft == null) {
				throw new IllegalStateException("Minecraft client is unavailable");
			}
		} catch (Throwable exception) {
			fail("Minecraft client became unavailable", exception, null);
			return;
		}

		boolean reportReadiness;
		synchronized (STATE_LOCK) {
			reportReadiness = !readinessReported;
			readinessReported = true;
		}
		if (reportReadiness) {
			PackForgeCore.refreshCompatibility();
			PackForge.LOGGER.info("PackForge runtime smoke ready: startupReloadComplete=true stabilizationMs={}", ACTION_DELAY_MILLIS);
		}
		runScenarioAttempt(minecraft);
	}

	private static void runScenarioAttempt(Minecraft minecraft) {
		final int attempt;
		final RuntimeSmokeScenario.Scenario selected;
		synchronized (STATE_LOCK) {
			if (!enabled || failed) {
				return;
			}
			attempt = scenarioAttempt;
			selected = scenario;
		}

		boolean injectFailure = selected.usesResourceFailureInjection() && attempt < failureAttemptCount(selected);
		if (!injectFailure) {
			synchronized (STATE_LOCK) {
				expectedFailureInFlight = false;
			}
		}
		if (injectFailure && !RuntimeSmokeScenario.armOneShotFailure()) {
			fail("could not arm deterministic resource failure", null, minecraft);
			return;
		}
		if (injectFailure) {
			synchronized (STATE_LOCK) {
				expectedFailureInFlight = true;
			}
		}
		boolean cancelInFlight = selected == RuntimeSmokeScenario.Scenario.CANCEL_IN_FLIGHT && attempt == 0;
		PackForge.LOGGER.info("PackForge runtime smoke scenario action: name={} attempt={} injectFailure={} cancelInFlight={}",
			selected.id(), attempt + 1, injectFailure, cancelInFlight);

		final CompletableFuture<Void> reload;
		try {
			reload = RuntimeSmokeMinecraftCompat.reloadForScenario(minecraft);
			if (reload == null) {
				throw new IllegalStateException("Minecraft returned no resource reload future");
			}
		} catch (Throwable exception) {
			dispatchAttemptResult(minecraft, attempt, null, exception, new AtomicBoolean());
			return;
		}

		AtomicBoolean dispatched = new AtomicBoolean();
		reload.whenComplete((ignored, error) -> dispatchAttemptResult(minecraft, attempt, reload, error, dispatched));
		if (cancelInFlight) {
			try {
				CompletableFuture.delayedExecutor(CANCELLATION_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
					try {
						minecraft.execute(() -> {
							if (reload.isDone()) {
								dispatchAttemptResult(minecraft, attempt, reload,
									new IllegalStateException("resource reload was already complete before cancellation"), dispatched);
							} else if (!reload.cancel(true)) {
								dispatchAttemptResult(minecraft, attempt, reload,
									new IllegalStateException("resource reload cancellation was rejected"), dispatched);
							}
						});
					} catch (Throwable exception) {
						fail("could not dispatch in-flight cancellation", exception, minecraft);
					}
				});
			} catch (Throwable exception) {
				fail("could not schedule in-flight cancellation", exception, minecraft);
			}
		}
	}

	private static void dispatchAttemptResult(
		Minecraft minecraft,
		int attempt,
		CompletableFuture<Void> reload,
		Throwable error,
		AtomicBoolean dispatched
	) {
		if (!dispatched.compareAndSet(false, true)) {
			return;
		}
		try {
			minecraft.execute(() -> handleAttemptResult(minecraft, attempt, reload, error));
		} catch (Throwable exception) {
			fail("could not dispatch scenario result to the Minecraft executor", exception, minecraft);
		}
	}

	private static void handleAttemptResult(Minecraft minecraft, int attempt, CompletableFuture<Void> reload, Throwable error) {
		final RuntimeSmokeScenario.Scenario selected;
		synchronized (STATE_LOCK) {
			if (!enabled || failed || attempt != scenarioAttempt) {
				return;
			}
			selected = scenario;
		}

		boolean expectedCancellation = selected == RuntimeSmokeScenario.Scenario.CANCEL_IN_FLIGHT && attempt == 0;
		boolean expectedFailure = selected.usesResourceFailureInjection() && attempt < failureAttemptCount(selected);
		boolean cancelled = reload != null && reload.isCancelled() || error instanceof CancellationException;
		if (expectedCancellation && !cancelled) {
			fail("reload did not enter the cancelled state", error, minecraft);
			return;
		}
		if (!expectedCancellation && expectedFailure && error == null) {
			fail("forced resource failure did not reach the reload future", null, minecraft);
			return;
		}
		if (!expectedCancellation && expectedFailure && !RuntimeSmokeScenario.isExpectedFailure(error)) {
			fail("reload failed without the deterministic injected failure marker", error, minecraft);
			return;
		}
		if (!expectedCancellation && !expectedFailure && error != null) {
			fail("recovery reload failed", error, minecraft);
			return;
		}

		synchronized (STATE_LOCK) {
			if (recordedAttempt != attempt) {
				recordedAttempt = attempt;
				cleanupPolls = 0;
				if (expectedCancellation) {
					cancellations++;
				} else if (expectedFailure) {
					injectedFailures++;
				}
			}
		}

		boolean futureSettled = reload == null || reload.isDone() || reload.isCancelled();
		boolean contextCleared = ReloadExecutionContext.current() == null;
		boolean scenarioOverlayCleared;
		synchronized (STATE_LOCK) {
			scenarioOverlayCleared = overlayCleared;
		}
		if (!futureSettled || !contextCleared) {
				int poll;
				synchronized (STATE_LOCK) {
					poll = ++cleanupPolls;
			}
			if (poll > MAX_CLEANUP_POLLS) {
				fail("reload cleanup did not settle", null, minecraft);
				return;
			}
				scheduleCleanupPoll(minecraft, attempt, reload, error);
				return;
			}
		if (!scenarioOverlayCleared) {
			fail("scenario overlay state was not cleared", null, minecraft);
			return;
		}

		boolean recovery = !expectedCancellation && !expectedFailure;
		if (recovery) {
			scheduleDelayedPass(minecraft, futureSettled, contextCleared);
			return;
		}

		synchronized (STATE_LOCK) {
			scenarioAttempt++;
			recordedAttempt = -1;
			pendingClientAction = true;
		}
		scheduleDelayedClientAction();
	}

	/** Called by the test-only Minecraft mixin before vanilla aborts the client. */
	public static boolean shouldSuppressVanillaRollback() {
		synchronized (STATE_LOCK) {
			return enabled && expectedFailureInFlight;
		}
	}

	private static void scheduleCleanupPoll(Minecraft minecraft, int attempt, CompletableFuture<Void> reload, Throwable error) {
		try {
			CompletableFuture.delayedExecutor(CLEANUP_POLL_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
				try {
					minecraft.execute(() -> handleAttemptResult(minecraft, attempt, reload, error));
				} catch (Throwable exception) {
					fail("could not dispatch cleanup check", exception, minecraft);
				}
			});
		} catch (Throwable exception) {
			fail("could not schedule cleanup check", exception, minecraft);
		}
	}

	private static int failureAttemptCount(RuntimeSmokeScenario.Scenario selected) {
		return selected == RuntimeSmokeScenario.Scenario.RETRY_EXHAUSTION ? 2 : 1;
	}

	public static void recordOverlayCleared() {
		synchronized (STATE_LOCK) {
			if (enabled && !failed) {
				overlayCleared = true;
			}
		}
	}

	private static void scheduleDelayedPass(Minecraft minecraft, boolean futureSettled, boolean contextCleared) {
		try {
			CompletableFuture.delayedExecutor(ACTION_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
				try {
					minecraft.execute(() -> finishPass(minecraft, futureSettled, contextCleared));
				} catch (Throwable exception) {
					fail("could not dispatch delayed scenario PASS", exception, minecraft);
				}
			});
		} catch (Throwable exception) {
			fail("could not schedule delayed scenario PASS", exception, minecraft);
		}
	}

	private static void finishPass(Minecraft minecraft, boolean futureSettled, boolean contextCleared) {
		RuntimeSmokeScenario.Scenario selected;
		int failures;
		int cancelled;
		int attempts;
		boolean scenarioOverlayCleared;
		synchronized (STATE_LOCK) {
			if (!enabled || failed) {
				return;
			}
			enabled = false;
			selected = scenario;
			failures = injectedFailures;
			cancelled = cancellations;
			attempts = scenarioAttempt + 1;
			scenarioOverlayCleared = overlayCleared;
		}
		PackForge.LOGGER.info(
			"PackForge runtime smoke scenario PASS: name={} attempts={} failures={} cancellations={} recovery=true contextCleared={} futureSettled={} overlayCleared={}",
			selected.id(), attempts, failures, cancelled, contextCleared, futureSettled, scenarioOverlayCleared);
		PackForge.LOGGER.info("PackForge runtime smoke complete: scenario={} reloads={} cleanExit=true", selected.id(), attempts);
		try {
			minecraft.stop();
		} catch (Throwable exception) {
			fail("could not stop Minecraft after scenario completion", exception, minecraft);
		}
	}

	private static void fail(String message, Throwable error, Minecraft minecraft) {
		synchronized (STATE_LOCK) {
			failLocked(message, error);
		}
		if (minecraft != null) {
			try {
				minecraft.stop();
			} catch (Throwable stopError) {
				PackForge.LOGGER.warn("PackForge runtime smoke scenario stop failed", stopError);
			}
		}
	}

	private static void failLocked(String message, Throwable error) {
		if (failed) {
			return;
		}
		failed = true;
		enabled = false;
		pendingClientAction = false;
		PackForge.LOGGER.error(
			"PackForge runtime smoke scenario FAIL: name={} attempts={} failures={} cancellations={} reason={}",
			scenario.id(), scenarioAttempt + 1, injectedFailures, cancellations, message, error);
	}
}
