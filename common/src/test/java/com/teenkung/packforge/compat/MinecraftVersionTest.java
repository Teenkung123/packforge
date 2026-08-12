package com.teenkung.packforge.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionTest {
	@Test
	void comparesNumericReleaseComponents() {
		assertTrue(MinecraftVersion.isAtLeast("1.21.11", "1.21.9"));
		assertTrue(MinecraftVersion.isAtLeast("1.21", "1.21.0"));
		assertTrue(MinecraftVersion.isAtLeast("26.2", "1.21.11"));
		assertFalse(MinecraftVersion.isAtLeast("1.21.9", "1.21.11"));
	}

	@Test
	void malformedOrMissingVersionsFailClosed() {
		assertFalse(MinecraftVersion.isAtLeast("", "1.21.11"));
		assertFalse(MinecraftVersion.isAtLeast("1.21.11-snapshot", "1.21.11"));
		assertFalse(MinecraftVersion.isAtLeast(null, "1.21.11"));
	}
}
