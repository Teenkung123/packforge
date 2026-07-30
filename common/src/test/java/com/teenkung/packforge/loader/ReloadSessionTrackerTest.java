package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadSessionTrackerTest {
	@Test
	void unchangedPackSelectionClearsAnOlderPendingDiff() {
		ReloadSessionTracker.capturePackDiff(List.of("vanilla", "old"), List.of("vanilla", "new"));
		ReloadSessionTracker.capturePackDiff(List.of("vanilla", "new"), List.of("vanilla", "new"));

		ReloadSessionTracker.ReloadSession session = ReloadSessionTracker.startReload();

		assertEquals("manual_or_startup", session.source());
		assertTrue(session.added().isEmpty());
		assertTrue(session.removed().isEmpty());
	}
}
