package com.teenkung.packforge.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickPackCompatibilityTest {
	private static final Set<PackForgeCapability> KNOWN_OVERLAPS = Set.of(
		PackForgeCapability.RESOURCE_PACK_INDEX,
		PackForgeCapability.LOADING_FADE_CONTROL,
		PackForgeCapability.FONT_PROVIDER_PRESELECTION,
		PackForgeCapability.ATLAS_MIP_PARALLEL
	);

	@Test
	void knownVersionsUseNarrowVersionAwareOwnership() {
		assertOwned("1.3.9", PackForgeCapability.RESOURCE_PACK_INDEX);
		assertOwned("1.4.0", PackForgeCapability.RESOURCE_PACK_INDEX, PackForgeCapability.LOADING_FADE_CONTROL);
		assertOwned(
			"forge-1.5.0+1.21.1",
			PackForgeCapability.RESOURCE_PACK_INDEX,
			PackForgeCapability.LOADING_FADE_CONTROL,
			PackForgeCapability.FONT_PROVIDER_PRESELECTION,
			PackForgeCapability.ATLAS_MIP_PARALLEL);
	}

	@Test
	void unknownAndFailedMetadataUseEveryKnownOverlap() {
		assertUnknown("");
		assertUnknown("not-a-version");
		assertUnknown("2.0.0");
		assertEquals(KNOWN_OVERLAPS, QuickPackCompatibility.Profile.detectionFailed().ownedCapabilities());
	}

	@Test
	void conservativeFallbackDoesNotClaimPackForgeOwnedCapabilities() {
		Set<PackForgeCapability> owned = QuickPackCompatibility.forTesting("future-release").ownedCapabilities();

		assertEquals(KNOWN_OVERLAPS, owned);
		assertFalse(owned.contains(PackForgeCapability.ZIP_READ_POOL));
		assertFalse(owned.contains(PackForgeCapability.LOADING_STATUS_OVERLAY));
	}

	@Test
	void absentQuickPackOwnsNothing() {
		QuickPackCompatibility.Profile profile = QuickPackCompatibility.absentForTesting();

		assertEquals(QuickPackCompatibility.Status.ABSENT, profile.status());
		assertTrue(profile.ownedCapabilities().isEmpty());
	}

	private static void assertUnknown(String rawVersion) {
		assertEquals(KNOWN_OVERLAPS, QuickPackCompatibility.forTesting(rawVersion).ownedCapabilities());
	}

	private static void assertOwned(String rawVersion, PackForgeCapability... expected) {
		assertEquals(Set.of(expected), QuickPackCompatibility.forTesting(rawVersion).ownedCapabilities());
	}
}
