package com.teenkung.packforge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_MIP_PARALLEL;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_PROVIDER_PRESELECTION;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_FADE_CONTROL;
import static com.teenkung.packforge.config.PackForgeCapability.LOADING_STATUS_OVERLAY;
import static com.teenkung.packforge.config.PackForgeCapability.RESOURCE_PACK_INDEX;
import static com.teenkung.packforge.config.PackForgeCapability.ZIP_READ_POOL;

import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FeaturePolicyTest {
	private static final Set<PackForgeCapability> QUICK_PACK_OWNED = EnumSet.of(
		RESOURCE_PACK_INDEX,
		FONT_PROVIDER_PRESELECTION,
		ATLAS_MIP_PARALLEL,
		LOADING_FADE_CONTROL
	);

	@Test
	void capabilityProfileIsTheOnlyFeatureSupportAuthority() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.startupOptimizerEnabled = true;
		Properties properties = new Properties();
		properties.setProperty("target", "test");
		properties.setProperty("capabilities", "RESOURCE_PACK_INDEX,STARTUP_OPTIMIZER");

		FeaturePolicy policy = FeaturePolicy.forTesting(config, PackForgeCapabilityProfile.fromProperties(properties));

		assertTrue(policy.loaderIndexEnabled());
		assertFalse(policy.loaderZipPoolEnabled());
		assertTrue(policy.startupOptimizerEnabled());
		assertFalse(policy.atlasCapEnabled());
	}

	@Test
	void zipPoolDefaultRequiresThePackForgeIndex() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		Properties properties = new Properties();
		properties.setProperty("target", "test");
		properties.setProperty("capabilities", "RESOURCE_PACK_INDEX,ZIP_READ_POOL");
		PackForgeCapabilityProfile capabilities = PackForgeCapabilityProfile.fromProperties(properties);

		FeaturePolicy enabled = FeaturePolicy.forTesting(config, capabilities);
		assertTrue(config.loaderZipPoolEnabled);
		assertTrue(enabled.loaderIndexEnabled());
		assertTrue(enabled.loaderZipPoolEnabled());

		config.loaderIndexEnabled = false;
		FeaturePolicy withoutIndex = FeaturePolicy.forTesting(config, capabilities);
		assertFalse(withoutIndex.loaderIndexEnabled());
		assertFalse(withoutIndex.loaderZipPoolEnabled());
	}

	@Test
	void unreachableCapabilitiesStayInactiveEvenWhenLegacyConfigEnablesThem() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.largeAtlasFixerEnabled = true;
		config.atlasMipParallelEnabled = true;
		config.modelParseTimingEnabled = true;
		config.modelAdaptiveBatchingEnabled = true;
		config.modelDuplicateParseCacheEnabled = true;
		config.startupOptimizerEnabled = true;
		config.startupAsyncDataParsingEnabled = true;
		config.startupAsyncClassScanEnabled = true;
		config.startupAsyncFontAtlasEnabled = true;
		Properties properties = new Properties();
		properties.setProperty("target", "test");
		properties.setProperty("capabilities", "RESOURCE_PACK_INDEX,MODEL_PARSE_BATCHING,STARTUP_OPTIMIZER");

		FeaturePolicy policy = FeaturePolicy.forTesting(config, PackForgeCapabilityProfile.fromProperties(properties));

		assertFalse(policy.atlasMipParallelEnabled());
		assertFalse(policy.modelParseTimingEnabled());
		assertFalse(policy.modelAdaptiveBatchingEnabled());
		assertFalse(policy.modelDuplicateParseCacheEnabled());
		assertFalse(policy.startupAsyncDataParsingEnabled());
		assertFalse(policy.startupAsyncClassScanEnabled());
		assertFalse(policy.startupAsyncFontAtlasEnabled());
		assertTrue(policy.modelParseBatchingEnabled());
	}

	@Test
	void policyCopiesMutableConfigAtTheBoundary() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		FeaturePolicy policy = FeaturePolicy.forTesting(config, PackForgeCapabilityProfile.currentDevelopment());

		config.reloadOptimizerEnabled = false;

		assertTrue(policy.reloadOptimizerEnabled());
		assertTrue(policy.modelParseBatchingEnabled());
	}

	@Test
	void atlasRetryShaderGuardChangesOnlyEffectiveState() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.largeAtlasFixerEnabled = true;
		config.atlasRetryEnabled = true;
		config.forceDisablePartIIIWithIris = true;
		Properties properties = new Properties();
		properties.setProperty("target", "test");
		properties.setProperty("capabilities", "ATLAS_RETRY");
		PackForgeCapabilityProfile capabilities = PackForgeCapabilityProfile.fromProperties(properties);

		assertFalse(FeaturePolicy.forTesting(config, capabilities, QuickPackCompatibility.absentForTesting(), true).atlasRetryEnabled());
		assertTrue(FeaturePolicy.forTesting(config, capabilities, QuickPackCompatibility.absentForTesting(), true).atlasRetryShaderGuardEnabled());
		assertTrue(config.atlasRetryEnabled);

		config.forceDisablePartIIIWithIris = false;
		assertTrue(FeaturePolicy.forTesting(config, capabilities, QuickPackCompatibility.absentForTesting(), true).atlasRetryEnabled());

		config.forceDisablePartIIIWithIris = true;
		assertTrue(FeaturePolicy.forTesting(config, capabilities, QuickPackCompatibility.absentForTesting(), false).atlasRetryEnabled());
	}

	@Test
	void quickPackOwnsOnlyItsVersionedOverlapCapabilities() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.loaderZipPoolEnabled = true;
		Properties properties = new Properties();
		properties.setProperty("target", "test");
		properties.setProperty("capabilities", "RESOURCE_PACK_INDEX,ZIP_READ_POOL,FONT_PROVIDER_PRESELECTION,ATLAS_MIP_PARALLEL,LOADING_FADE_CONTROL,LOADING_STATUS_OVERLAY,MODEL_PARSE_BATCHING");

		FeaturePolicy policy = FeaturePolicy.forTesting(
			config,
			PackForgeCapabilityProfile.fromProperties(properties),
			QuickPackCompatibility.forTesting("1.5.7")
		);

		assertTrue(policy.quickPackLoaded());
		assertEquals(QUICK_PACK_OWNED, policy.quickPackProfile().ownedCapabilities());
		for (PackForgeCapability capability : PackForgeCapability.values()) {
			assertEquals(QUICK_PACK_OWNED.contains(capability), policy.quickPackOwns(capability), capability.name());
		}
		assertFalse(policy.loaderIndexEnabled());
		assertFalse(policy.loaderZipPoolEnabled());
		assertFalse(policy.fontPrepareProviderSelectionEnabled());
		assertFalse(policy.atlasMipParallelEnabled());
		assertFalse(policy.loadingScreenFadeOutDisabled());
		assertTrue(policy.loadingStatusOverlayEnabled());
		assertTrue(policy.modelParseBatchingEnabled());
		assertTrue(policy.quickPackDisabledReason(RESOURCE_PACK_INDEX).contains("overlapping module"));
	}

	@Test
	void quickPackLeavesEveryConfiguredNonOverlapCapabilityEffective() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.reloadOptimizerEnabled = true;
		config.largeAtlasFixerEnabled = true;
		config.loaderTimingsEnabled = true;
		config.reloadListenerTimingsEnabled = true;
		config.shaderApplyStallDiagnosticsEnabled = true;
		config.immediatelyFastFontAtlasCompatEnabled = true;
		config.reloadSummaryToastEnabled = true;
		config.fontReloadDiagnosticsEnabled = true;
		config.fontBitmapProviderCacheEnabled = true;
		config.atlasPhaseTimingsEnabled = true;
		config.atlasDecodeBatchingEnabled = true;
		config.modelParseBatchingEnabled = true;
		config.modelParseTimingEnabled = true;
		config.modelAdaptiveBatchingEnabled = true;
		config.modelDuplicateParseCacheEnabled = true;
		config.modelUvTransparencyClampEnabled = true;
		config.atlasCapEnabled = true;
		config.atlasRetryEnabled = true;
		config.startupOptimizerEnabled = true;
		config.startupTimingsEnabled = true;
		config.startupStatusOverlayEnabled = true;
		config.startupExecutorTuningEnabled = true;
		config.startupAsyncDataParsingEnabled = true;
		config.startupAsyncClassScanEnabled = true;
		config.startupAsyncFontAtlasEnabled = true;

		FeaturePolicy policy = FeaturePolicy.forTesting(
			config,
			PackForgeCapabilityProfile.currentDevelopment(),
			QuickPackCompatibility.forTesting("future-version")
		);

		EnumSet<PackForgeCapability> retained = EnumSet.allOf(PackForgeCapability.class);
		retained.removeAll(QUICK_PACK_OWNED);
		for (PackForgeCapability capability : retained) {
			assertFalse(policy.quickPackOwns(capability), capability.name());
		}
		assertFalse(policy.loaderZipPoolEnabled());
		assertTrue(policy.loaderTimingsEnabled());
		assertTrue(policy.reloadListenerTimingsEnabled());
		assertTrue(policy.shaderApplyStallDiagnosticsEnabled());
		assertTrue(policy.immediatelyFastFontAtlasCompatEnabled());
		assertTrue(policy.reloadSummaryToastEnabled());
		assertTrue(policy.fontReloadDiagnosticsEnabled());
		assertTrue(policy.fontBitmapProviderCacheEnabled());
		assertTrue(policy.atlasPhaseTimingsEnabled());
		assertTrue(policy.atlasDecodeBatchingEnabled());
		assertTrue(policy.modelParseBatchingEnabled());
		assertTrue(policy.modelParseTimingEnabled());
		assertTrue(policy.modelAdaptiveBatchingEnabled());
		assertTrue(policy.modelDuplicateParseCacheEnabled());
		assertTrue(policy.modelUvTransparencyClampEnabled());
		assertTrue(policy.atlasCapEnabled());
		assertTrue(policy.atlasRetryEnabled());
		assertTrue(policy.startupOptimizerEnabled());
		assertTrue(policy.startupTimingsEnabled());
		assertTrue(policy.startupStatusOverlayEnabled());
		assertTrue(policy.startupExecutorTuningEnabled());
		assertTrue(policy.startupAsyncDataParsingEnabled());
		assertTrue(policy.startupAsyncClassScanEnabled());
		assertTrue(policy.startupAsyncFontAtlasEnabled());
	}

	@Test
	void quickPackOwnershipTracksVersionCapabilities() {
		QuickPackCompatibility.Profile old = QuickPackCompatibility.forTesting("1.3.9");
		QuickPackCompatibility.Profile fade = QuickPackCompatibility.forTesting("forge-1.4.0+1.21.1");
		QuickPackCompatibility.Profile current = QuickPackCompatibility.forTesting("1.5.0+1.21.1");

		assertEquals(Set.of(RESOURCE_PACK_INDEX), old.ownedCapabilities());
		assertEquals(Set.of(RESOURCE_PACK_INDEX, LOADING_FADE_CONTROL), fade.ownedCapabilities());
		assertEquals(QUICK_PACK_OWNED, current.ownedCapabilities());
	}

	@Test
	void unknownQuickPackVersionOnlyUsesConservativeKnownOverlap() {
		QuickPackCompatibility.Profile profile = QuickPackCompatibility.forTesting("not-a-version");

		assertEquals(QuickPackCompatibility.Status.MODULE_HANDOFF, profile.status());
		assertTrue(profile.owns(RESOURCE_PACK_INDEX));
		assertEquals(QUICK_PACK_OWNED, profile.ownedCapabilities());
		assertFalse(profile.owns(ZIP_READ_POOL));
		assertFalse(profile.owns(LOADING_STATUS_OVERLAY));
	}

	@Test
	void metadataDetectionFailureUsesTheSameConservativeOverlap() {
		QuickPackCompatibility.Profile profile = QuickPackCompatibility.Profile.detectionFailed();

		assertEquals(QuickPackCompatibility.Status.DETECTION_FAILED, profile.status());
		assertEquals(QUICK_PACK_OWNED, profile.ownedCapabilities());
	}

	@Test
	void blankAndFutureQuickPackVersionsUseAllKnownOverlapCapabilities() {
		assertEquals(QUICK_PACK_OWNED, QuickPackCompatibility.forTesting(null).ownedCapabilities());
		assertEquals(QUICK_PACK_OWNED, QuickPackCompatibility.forTesting("   ").ownedCapabilities());
		assertEquals(QUICK_PACK_OWNED, QuickPackCompatibility.forTesting("2.0.0").ownedCapabilities());
	}
}
