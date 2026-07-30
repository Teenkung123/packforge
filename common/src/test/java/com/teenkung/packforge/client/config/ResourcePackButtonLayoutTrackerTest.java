package com.teenkung.packforge.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackButtonLayoutTrackerTest {
	@Test
	void lateWidgetAdditionTriggersOneAdditionalReflow() {
		ResourcePackButtonLayoutTracker tracker = new ResourcePackButtonLayoutTracker();
		long initial = ResourcePackButtonLayoutTracker.beginSignature(854, 480);
		long withLateButton = ResourcePackButtonLayoutTracker.includeWidget(
			initial,
			17,
			468,
			440,
			24,
			20,
			true
		);

		assertTrue(tracker.shouldReflow(initial, false));
		assertFalse(tracker.shouldReflow(initial, false));
		assertNotEquals(initial, withLateButton);
		assertTrue(tracker.shouldReflow(withLateButton, true));
		assertFalse(tracker.shouldReflow(withLateButton, false));
	}

	@Test
	void movementVisibilityAndScreenResizeChangeTheSignature() {
		long initial = ResourcePackButtonLayoutTracker.beginSignature(854, 480);
		long visible = ResourcePackButtonLayoutTracker.includeWidget(initial, 17, 468, 440, 24, 20, true);
		long moved = ResourcePackButtonLayoutTracker.includeWidget(initial, 17, 496, 440, 24, 20, true);
		long hidden = ResourcePackButtonLayoutTracker.includeWidget(initial, 17, 468, 440, 24, 20, false);
		long resized = ResourcePackButtonLayoutTracker.beginSignature(640, 360);

		assertNotEquals(visible, moved);
		assertNotEquals(visible, hidden);
		assertNotEquals(initial, resized);
	}

	@Test
	void collisionForcesRecoveryEvenWhenTheLayoutSignatureIsUnchanged() {
		ResourcePackButtonLayoutTracker tracker = new ResourcePackButtonLayoutTracker();
		long signature = ResourcePackButtonLayoutTracker.beginSignature(854, 480);

		assertTrue(tracker.shouldReflow(signature, false));
		assertFalse(tracker.shouldReflow(signature, false));
		assertTrue(tracker.shouldReflow(signature, true));
	}
}
