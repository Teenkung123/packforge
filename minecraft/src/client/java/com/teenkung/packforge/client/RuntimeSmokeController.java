package com.teenkung.packforge.client;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadHooks;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional, property-gated client reload controller used by runtime smoke tests.
 * It is deliberately inert unless the property is present and valid.
 */
public final class RuntimeSmokeController {
	private static final String RELOAD_COUNT_PROPERTY = "packforge.runtimeSmokeReloadCount";
	private static final int MAX_RELOAD_COUNT = 32;
	private static final long ACTION_DELAY_MILLIS = 1_000L;
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
	private static final Object STATE_LOCK = new Object();
	private static final Set<ReloadExecutionContext> HANDLED_COMPLETIONS =
		Collections.newSetFromMap(new IdentityHashMap<>());

	private static boolean enabled;
	private static boolean initialCompletionObserved;
	private static boolean pendingClientAction;
	private static boolean readinessReported;
	private static boolean failed;
	private static int remainingReloads;
	private static int completedRequestedReloads;

	private RuntimeSmokeController() {
	}

	public static void init() {
		if (!INITIALIZED.compareAndSet(false, true)) {
			return;
		}

		String property;
		try {
			property = System.getProperty(RELOAD_COUNT_PROPERTY);
		} catch (SecurityException exception) {
			PackForge.LOGGER.warn("PackForge runtime smoke controller is disabled: cannot read {}", RELOAD_COUNT_PROPERTY, exception);
			return;
		}
		if (property == null) {
			return;
		}

		int reloadCount;
		try {
			reloadCount = Integer.parseInt(property.trim());
		} catch (NumberFormatException exception) {
			PackForge.LOGGER.warn("PackForge runtime smoke controller is disabled: {} must be a non-negative integer, got '{}'",
				RELOAD_COUNT_PROPERTY, property);
			return;
		}
		if (reloadCount < 0) {
			PackForge.LOGGER.warn("PackForge runtime smoke controller is disabled: {} must be non-negative, got {}",
				RELOAD_COUNT_PROPERTY, reloadCount);
			return;
		}
		if (reloadCount > MAX_RELOAD_COUNT) {
			PackForge.LOGGER.warn("PackForge runtime smoke controller is disabled: {} must not exceed {}, got {}",
				RELOAD_COUNT_PROPERTY, MAX_RELOAD_COUNT, reloadCount);
			return;
		}

		synchronized (STATE_LOCK) {
			enabled = true;
			remainingReloads = reloadCount;
		}
		ReloadHooks.registerCompletionHook(RuntimeSmokeController::onReloadCompletion);
		PackForge.LOGGER.info("PackForge runtime smoke controller enabled: requestedReloads={}", reloadCount);
	}

	private static void onReloadCompletion(ReloadExecutionContext context, Throwable error) {
		if (context == null) {
			return;
		}

		boolean scheduleAction = false;
		synchronized (STATE_LOCK) {
			if (!enabled || failed || initialCompletionObserved || !HANDLED_COMPLETIONS.add(context)) {
				return;
			}
			if (error != null) {
				failLocked("resource reload failed; controller is inert", error);
				return;
			}
			if (pendingClientAction) {
				return;
			}

			initialCompletionObserved = true;
			pendingClientAction = true;
			scheduleAction = true;
		}

		if (scheduleAction) {
			scheduleDelayedClientAction();
		}
	}

	private static void scheduleDelayedClientAction() {
		final Minecraft minecraft;
		try {
			minecraft = Minecraft.getInstance();
			if (minecraft == null) {
				throw new IllegalStateException("Minecraft client is unavailable");
			}
			CompletableFuture.delayedExecutor(ACTION_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
				try {
					minecraft.execute(RuntimeSmokeController::runPendingClientAction);
				} catch (Throwable exception) {
					fail("could not schedule runtime smoke action on the Minecraft client executor", exception);
				}
			});
		} catch (Throwable exception) {
			fail("could not schedule runtime smoke action", exception);
		}
	}

	private static void runPendingClientAction() {
		final Minecraft minecraft;
		final int requestNumber;
		final boolean requestReload;
		final boolean reportReadiness;
		synchronized (STATE_LOCK) {
			if (!enabled || failed || !pendingClientAction) {
				return;
			}
			pendingClientAction = false;
			try {
				minecraft = Minecraft.getInstance();
				if (minecraft == null) {
					throw new IllegalStateException("Minecraft client is unavailable");
				}
			} catch (Throwable exception) {
				failLocked("Minecraft client became unavailable; controller is inert", exception);
				return;
			}

			requestReload = remainingReloads > 0;
			reportReadiness = !readinessReported;
			readinessReported = true;
			if (requestReload) {
				requestNumber = completedRequestedReloads + 1;
				remainingReloads--;
			} else {
				requestNumber = 0;
				enabled = false;
			}
		}
		if (reportReadiness) {
			PackForge.LOGGER.info("PackForge runtime smoke ready: startupReloadComplete=true stabilizationMs={}",
				ACTION_DELAY_MILLIS);
		}

		if (!requestReload) {
			PackForge.LOGGER.info("PackForge runtime smoke complete: reloads={}", completedRequestedReloads);
			try {
				minecraft.stop();
			} catch (Throwable exception) {
				fail("could not stop Minecraft after runtime smoke completion", exception);
			}
			return;
		}

		PackForge.LOGGER.info("PackForge runtime smoke reload requested: number={} remaining={}",
			requestNumber, remainingReloads);
		try {
			CompletableFuture<Void> reload = minecraft.reloadResourcePacks();
			if (reload == null) {
				throw new IllegalStateException("Minecraft returned no resource reload future");
			}
			reload.whenComplete((ignored, error) -> {
				if (error != null) {
					fail("requested resource reload failed; controller is inert", error);
					return;
				}
				synchronized (STATE_LOCK) {
					if (!enabled || failed || pendingClientAction) {
						return;
					}
					completedRequestedReloads++;
					pendingClientAction = true;
				}
				scheduleDelayedClientAction();
			});
		} catch (Throwable exception) {
			fail("could not request resource reload; controller is inert", exception);
		}
	}

	private static void fail(String message, Throwable error) {
		synchronized (STATE_LOCK) {
			failLocked(message, error);
		}
	}

	private static void failLocked(String message, Throwable error) {
		if (failed) {
			return;
		}
		failed = true;
		enabled = false;
		pendingClientAction = false;
		PackForge.LOGGER.error("PackForge runtime smoke failure: {}", message, error);
	}
}
