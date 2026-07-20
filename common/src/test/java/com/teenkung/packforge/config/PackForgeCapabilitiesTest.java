package com.teenkung.packforge.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeCapabilitiesTest {
	@Test
	void parsesGeneratedLegacySubsetAndReasons() throws Exception {
		String resource = """
			target=mc1_21_1
			capabilities=RESOURCE_PACK_INDEX,ZIP_READ_POOL,LOADER_TIMINGS,UNKNOWN_VALUE
			reason.ATLAS_CAP=No legacy atlas adapter
			""";
		PackForgeCapabilityProfile profile = PackForgeCapabilities.load(
			new ByteArrayInputStream(resource.getBytes(StandardCharsets.ISO_8859_1))
		);

		assertEquals("mc1_21_1", profile.target());
		assertTrue(profile.supports(PackForgeCapability.RESOURCE_PACK_INDEX));
		assertTrue(profile.supports(PackForgeCapability.ZIP_READ_POOL));
		assertFalse(profile.supports(PackForgeCapability.ATLAS_CAP));
		assertEquals("No legacy atlas adapter", profile.unavailableReason(PackForgeCapability.ATLAS_CAP).orElseThrow());
		assertEquals(java.util.Set.of("UNKNOWN_VALUE"), profile.unknownIdentifiers());
	}

	@Test
	void presentResourceWithoutCapabilityListFailsClosed() {
		Properties properties = new Properties();
		properties.setProperty("target", "minimal");
		PackForgeCapabilityProfile profile = PackForgeCapabilityProfile.fromProperties(properties);
		assertTrue(profile.available().isEmpty());
		assertEquals(PackForgeCapability.values().length, profile.unavailable().size());
	}

	@Test
	void absentDevelopmentProfileEnablesCurrentDeliveredCapabilities() {
		PackForgeCapabilityProfile profile = PackForgeCapabilityProfile.currentDevelopment();
		assertEquals(PackForgeCapability.values().length, profile.available().size());
	}

	@Test
	void safeFallbackDisablesEveryCapability() {
		PackForgeCapabilityProfile profile = PackForgeCapabilityProfile.safeFallback();
		assertTrue(profile.available().isEmpty());
		assertEquals(PackForgeCapability.values().length, profile.unavailable().size());
	}
}
