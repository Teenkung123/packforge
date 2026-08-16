package com.teenkung.packforge.loader;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Test-only controls for deterministic runtime-smoke scenarios.
 *
 * <p>The selector is deliberately inert unless a harness configures a scenario
 * and explicitly arms the matching action.  In particular, selecting
 * {@link Scenario#FORCED_RESOURCE_FAILURE} does not fail a normal resource
 * reload by itself.</p>
 */
public final class RuntimeSmokeScenario {
	public static final String PROPERTY_NAME = "packforge.runtimeSmokeScenario";

	private static final String FAILURE_MESSAGE = "PackForge forced resource reload failure";
	private static final Object PROPERTY_LOCK = new Object();

	private static volatile Scenario configured = Scenario.NONE;
	private static volatile boolean propertyLoaded;
	private static boolean failureArmed;

	private RuntimeSmokeScenario() {
	}

	/** Runtime-smoke selectors understood by the future scenario controller. */
	public enum Scenario {
		NONE("none"),
		REPEAT("repeat"),
		CANCEL_IN_FLIGHT("cancel-in-flight"),
		FORCED_RESOURCE_FAILURE("forced-resource-failure"),
		RETRY_SUCCESS("retry-success"),
		RETRY_EXHAUSTION("retry-exhaustion");

		private final String id;

		Scenario(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public boolean usesResourceFailureInjection() {
			return this == FORCED_RESOURCE_FAILURE || this == RETRY_SUCCESS || this == RETRY_EXHAUSTION;
		}

		/** Unknown, blank, and missing values fail closed to {@link #NONE}. */
		public static Scenario parse(String value) {
			if (value == null || value.isBlank()) {
				return NONE;
			}
			String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
			for (Scenario scenario : values()) {
				if (scenario.id.equals(normalized)) {
					return scenario;
				}
			}
			return NONE;
		}
	}

	/** Returns the configured selector, lazily reading the test property once. */
	public static Scenario configured() {
		if (!propertyLoaded) {
			loadProperty();
		}
		return configured;
	}

	/** Configures a selector directly, primarily for a runtime harness or test. */
	public static Scenario configure(String value) {
		return configure(Scenario.parse(value));
	}

	/** Configures a selector directly, primarily for a runtime harness or test. */
	public static Scenario configure(Scenario scenario) {
		Scenario next = scenario == null ? Scenario.NONE : scenario;
		synchronized (PROPERTY_LOCK) {
			configured = next;
			failureArmed = false;
			propertyLoaded = true;
		}
		return next;
	}

	/** Reads and applies {@link #PROPERTY_NAME} explicitly for a smoke harness. */
	public static Scenario configureFromSystemProperty() {
		String value;
		try {
			value = System.getProperty(PROPERTY_NAME);
		} catch (SecurityException ignored) {
			value = null;
		}
		return configure(value);
	}

	/**
	 * Arms exactly one forced failure when the selected scenario supports it.
	 *
	 * @return {@code true} only when this call changed the state from unarmed to
	 * armed
	 */
	public static boolean armOneShotFailure() {
		if (!configured().usesResourceFailureInjection()) {
			return false;
		}
		synchronized (PROPERTY_LOCK) {
			if (!configured.usesResourceFailureInjection() || failureArmed) {
				return false;
			}
			failureArmed = true;
			return true;
		}
	}

	/**
	 * Returns the deterministic failure for the next resource-listener future when
	 * a forced-failure action was armed. The arm is consumed before returning so
	 * a retry is not failed implicitly and the exception is delivered through
	 * Minecraft's normal future/error path rather than thrown on the render thread.
	 */
	public static Throwable consumeFailure() {
		boolean fail;
		synchronized (PROPERTY_LOCK) {
			fail = configured.usesResourceFailureInjection() && failureArmed;
			if (fail) {
				failureArmed = false;
			}
		}
		return fail ? new IllegalStateException(FAILURE_MESSAGE) : null;
	}

	/** Returns whether an error contains the exact failure injected by this harness. */
	public static boolean isExpectedFailure(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (FAILURE_MESSAGE.equals(current.getMessage())) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	/**
	 * Turns one armed failure into a failed listener future after the original
	 * listener has settled, preserving Minecraft's normal reload error path.
	 */
	public static CompletableFuture<?> injectFailureAfter(CompletableFuture<?> future) {
		if (future == null) {
			throw new IllegalArgumentException("future");
		}
		Throwable failure = consumeFailure();
		if (failure == null) {
			return future;
		}
		return future.handle((ignored, ignoredError) -> null)
			.thenCompose(ignored -> CompletableFuture.failedFuture(failure));
	}

	private static void loadProperty() {
		synchronized (PROPERTY_LOCK) {
			if (propertyLoaded) {
				return;
			}
			String value;
			try {
				value = System.getProperty(PROPERTY_NAME);
			} catch (SecurityException ignored) {
				value = null;
			}
			configured = Scenario.parse(value);
			propertyLoaded = true;
		}
	}

	static void resetForTesting() {
		synchronized (PROPERTY_LOCK) {
			configured = Scenario.NONE;
			failureArmed = false;
			propertyLoaded = true;
		}
	}
}
