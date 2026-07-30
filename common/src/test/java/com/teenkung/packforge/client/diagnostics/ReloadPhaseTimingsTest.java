package com.teenkung.packforge.client.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadPhaseTimingsTest {
	@Test
	void combinesRepeatedStringKeysAndSortsSnapshot() {
		ReloadPhaseTimings timings = new ReloadPhaseTimings();
		timings.record("zeta", timings.start());
		timings.record("alpha", timings.start());
		timings.record("zeta", timings.start());
		assertEquals("alpha", timings.snapshot().get(0).name());
		assertEquals("zeta", timings.snapshot().get(1).name());
		assertTrue(timings.snapshot().get(1).elapsedNs() >= 0L);
	}
}
