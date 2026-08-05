package com.teenkung.packforge.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ModelSchedulingPlanTest {
	@Test
	void noModelFeatureRequestUsesOriginal() {
		ModelSchedulingPlan plan = ModelSchedulingPlan.fromFlags(false, false, false, false, 0);

		assertEquals(ModelSchedulingPlan.Strategy.ORIGINAL, plan.strategy());
		assertEquals(1, plan.workerBudget());
	}

	@Test
	void everyModelFeatureRequestUsesHookPreservingCoalescing() {
		boolean[][] requests = {
			{true, false, false, false},
			{false, true, false, false},
			{false, false, true, false},
			{false, false, false, true}
		};

		for (boolean[] request : requests) {
			ModelSchedulingPlan plan = ModelSchedulingPlan.fromFlags(
				request[0], request[1], request[2], request[3], 7
			);
			assertEquals(ModelSchedulingPlan.Strategy.COALESCED_ORIGINAL, plan.strategy());
			assertEquals(7, plan.workerBudget());
		}
	}

	@Test
	void directBatchedIsNeverSelected() {
		ModelSchedulingPlan original = ModelSchedulingPlan.fromFlags(false, false, false, false, 3);
		ModelSchedulingPlan coalesced = ModelSchedulingPlan.fromFlags(true, true, true, true, 3);

		assertNotEquals(ModelSchedulingPlan.Strategy.DIRECT_BATCHED, original.strategy());
		assertNotEquals(ModelSchedulingPlan.Strategy.DIRECT_BATCHED, coalesced.strategy());
	}
}
