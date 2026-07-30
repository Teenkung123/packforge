package com.teenkung.packforge.client.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelBatchPlanTest {
	@Test
	void createsStableContiguousRanges() {
		assertEquals(List.of(new ModelBatchPlan.Range(0, 64), new ModelBatchPlan.Range(64, 128), new ModelBatchPlan.Range(128, 130)),
			ModelBatchPlan.create(130, 64, false));
	}

	@Test
	void matchesAdaptiveThresholds() {
		assertEquals(32, ModelBatchPlan.create(512, 64, true).get(0).toExclusive());
		assertEquals(96, ModelBatchPlan.create(2048, 64, true).get(0).toExclusive());
		assertEquals(128, ModelBatchPlan.create(4096, 64, true).get(0).toExclusive());
	}
}
