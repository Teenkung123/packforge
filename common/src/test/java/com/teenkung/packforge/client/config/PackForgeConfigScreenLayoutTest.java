package com.teenkung.packforge.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeConfigScreenLayoutTest {
	@Test
	void compactScreenReducesRowsBeforeTheFooter() {
		int screenHeight = 240;
		int pageSize = PackForgeConfigScreenLayout.pageSize(screenHeight);
		int lastRowBottom = 58 + (pageSize - 1) * 28 + 20;
		int navigationTop = screenHeight - 60;

		assertEquals(4, pageSize);
		assertTrue(lastRowBottom <= navigationTop);
	}

	@Test
	void tallScreenRetainsTheSevenRowPage() {
		assertEquals(7, PackForgeConfigScreenLayout.pageSize(480));
	}

	@Test
	void compactSaveErrorGetsItsOwnBand() {
		int screenHeight = 240;
		int pageSize = PackForgeConfigScreenLayout.pageSize(screenHeight, true);
		int lastRowBottom = 58 + (pageSize - 1) * 28 + 20;
		int errorTop = screenHeight - 84;

		assertEquals(3, pageSize);
		assertTrue(lastRowBottom < errorTop);
	}
}
