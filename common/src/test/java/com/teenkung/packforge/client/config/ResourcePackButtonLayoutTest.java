package com.teenkung.packforge.client.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackButtonLayoutTest {
	private static final ResourcePackButtonLayout.Rectangle SCREEN = rectangle(0, 0, 854, 480);
	private static final ResourcePackButtonLayout.ActionButtonAnchors ANCHORS = anchors(168, 440, 300, 440);

	@Test
	void usesTheSpaceImmediatelyAfterDoneWhenItIsFree() {
		Optional<ResourcePackButtonLayout.Rectangle> placement = find(SCREEN, ANCHORS, List.of());

		assertEquals(rectangle(468, 440, 20, 20), placement.orElseThrow());
	}

	@Test
	void placesAfterEntityFeaturesExactTwentyFourByTwentyButton() {
		ResourcePackButtonLayout.Rectangle entityFeatures = rectangle(468, 440, 24, 20);

		Optional<ResourcePackButtonLayout.Rectangle> placement = find(SCREEN, ANCHORS, List.of(entityFeatures));

		assertEquals(rectangle(496, 440, 20, 20), placement.orElseThrow());
		assertFalse(placement.orElseThrow().overlaps(entityFeatures));
	}

	@Test
	void expandsPastACompleteRightHandButtonCluster() {
		List<ResourcePackButtonLayout.Rectangle> occupied = List.of(
			rectangle(468, 440, 24, 20),
			rectangle(496, 440, 20, 20),
			rectangle(520, 440, 30, 20)
		);

		assertEquals(rectangle(554, 440, 20, 20), find(SCREEN, ANCHORS, occupied).orElseThrow());
	}

	@Test
	void usesMirroredLeftSlotWhenTheRightClusterRunsOutOfRoom() {
		ResourcePackButtonLayout.Rectangle narrowScreen = rectangle(0, 0, 530, 480);
		List<ResourcePackButtonLayout.Rectangle> occupied = List.of(rectangle(468, 440, 40, 20));

		assertEquals(rectangle(140, 440, 20, 20), find(narrowScreen, ANCHORS, occupied).orElseThrow());
	}

	@Test
	void usesTheRowAboveWhenBothBottomClustersAreOccupied() {
		List<ResourcePackButtonLayout.Rectangle> occupied = List.of(
			rectangle(468, 440, 380, 20),
			rectangle(0, 440, 160, 20)
		);

		assertEquals(rectangle(468, 416, 20, 20), find(SCREEN, ANCHORS, occupied).orElseThrow());
	}

	@Test
	void resizeInputsProduceInBoundsNonOverlappingPlacements() {
		ResourcePackButtonLayout.Rectangle resizedScreen = rectangle(0, 0, 640, 360);
		ResourcePackButtonLayout.ActionButtonAnchors resizedAnchors = anchors(61, 320, 220, 320);
		List<ResourcePackButtonLayout.Rectangle> occupied = List.of(rectangle(388, 320, 24, 20));

		ResourcePackButtonLayout.Rectangle placement = find(resizedScreen, resizedAnchors, occupied).orElseThrow();

		assertTrue(resizedScreen.contains(placement));
		assertTrue(occupied.stream().noneMatch(placement::overlaps));
		assertEquals(rectangle(416, 320, 20, 20), placement);
	}

	@Test
	void returnsEmptyWhenNoCandidateRowHasSpace() {
		ResourcePackButtonLayout.Rectangle tinyScreen = rectangle(0, 0, 530, 480);
		List<ResourcePackButtonLayout.Rectangle> occupied = List.of(
			rectangle(468, 440, 40, 20),
			rectangle(0, 440, 160, 20),
			rectangle(468, 416, 40, 20)
		);

		assertTrue(find(tinyScreen, ANCHORS, occupied).isEmpty());
	}

	private static Optional<ResourcePackButtonLayout.Rectangle> find(
		ResourcePackButtonLayout.Rectangle screen,
		ResourcePackButtonLayout.ActionButtonAnchors anchors,
		List<ResourcePackButtonLayout.Rectangle> occupied
	) {
		return ResourcePackButtonLayout.findPlacement(screen, anchors, 20, 20, 8, 4, occupied);
	}

	private static ResourcePackButtonLayout.ActionButtonAnchors anchors(int openX, int y, int doneX, int doneY) {
		return new ResourcePackButtonLayout.ActionButtonAnchors(rectangle(openX, y, 300, 20), rectangle(doneX, doneY, 160, 20));
	}

	private static ResourcePackButtonLayout.Rectangle rectangle(int x, int y, int width, int height) {
		return new ResourcePackButtonLayout.Rectangle(x, y, width, height);
	}
}
