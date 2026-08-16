package com.teenkung.packforge.loader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSmokeScenarioTest {
	@AfterEach
	void resetScenario() {
		RuntimeSmokeScenario.resetForTesting();
	}

	@Test
	void parsesKnownSelectorsAndFailsClosedForUnknownValues() {
		assertEquals(RuntimeSmokeScenario.Scenario.REPEAT, RuntimeSmokeScenario.Scenario.parse("repeat"));
		assertEquals(RuntimeSmokeScenario.Scenario.CANCEL_IN_FLIGHT,
			RuntimeSmokeScenario.Scenario.parse("CANCEL_IN_FLIGHT"));
		assertEquals(RuntimeSmokeScenario.Scenario.RETRY_SUCCESS,
			RuntimeSmokeScenario.Scenario.parse("retry-success"));
		assertEquals(RuntimeSmokeScenario.Scenario.NONE, RuntimeSmokeScenario.Scenario.parse("unknown"));
		assertEquals(RuntimeSmokeScenario.Scenario.NONE, RuntimeSmokeScenario.Scenario.parse(" "));
	}

	@Test
	void forcedFailureIsInertUntilArmedAndConsumedOnce() {
		RuntimeSmokeScenario.configure(RuntimeSmokeScenario.Scenario.FORCED_RESOURCE_FAILURE);

		assertEquals(null, RuntimeSmokeScenario.consumeFailure());
		assertTrue(RuntimeSmokeScenario.armOneShotFailure());
		assertFalse(RuntimeSmokeScenario.armOneShotFailure());
		Throwable failure = RuntimeSmokeScenario.consumeFailure();
		assertTrue(failure instanceof IllegalStateException);
		assertTrue(RuntimeSmokeScenario.isExpectedFailure(failure));
		assertEquals(null, RuntimeSmokeScenario.consumeFailure());
	}

	@Test
	void retryScenarioCanArmTheSameOneShotFailurePrimitive() {
		RuntimeSmokeScenario.configure(RuntimeSmokeScenario.Scenario.RETRY_SUCCESS);

		assertTrue(RuntimeSmokeScenario.armOneShotFailure());
		assertTrue(RuntimeSmokeScenario.isExpectedFailure(RuntimeSmokeScenario.consumeFailure()));
		assertEquals(null, RuntimeSmokeScenario.consumeFailure());
	}
}
