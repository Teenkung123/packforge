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
		ZIP_READ_POOL,
		FONT_PROVIDER_PRESELECTION,
		ATLAS_MIP_PARALLEL,
		LOADING_FADE_CONTROL,
		LOADING_STATUS_OVERLAY
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
	void policyCopiesMutableConfigAtTheBoundary() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		FeaturePolicy policy = FeaturePolicy.forTesting(config, PackForgeCapabilityProfile.currentDevelopment());

		config.reloadOptimizerEnabled = false;

		assertTrue(policy.reloadOptimizerEnabled());
		assertTrue(policy.modelParseBatchingEnabled());
	}

	@Test
	void quickPackOwnsExactlyTheSixOverlapCapabilities() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
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
		assertFalse(policy.loadingStatusOverlayEnabled());
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
	void quickPackVersionMetadataIsInformationalOnly() {
		for (String version : new String[] {"1.4.0", "1.5.0", "2.0.0", "12.4.1", "not-a-version", null}) {
			QuickPackCompatibility.Profile profile = QuickPackCompatibility.forTesting(version);

			assertEquals(QuickPackCompatibility.Status.MODULE_HANDOFF, profile.status());
			assertTrue(profile.owns(RESOURCE_PACK_INDEX));
			assertTrue(profile.disabledReason(RESOURCE_PACK_INDEX).contains("overlapping module"));
		}
	}

	@Test
	void unreadableQuickPackVersionStillHandsOffEveryOverlapModule() {
		QuickPackCompatibility.Profile profile = QuickPackCompatibility.forTesting("not-a-version");

		assertEquals(QuickPackCompatibility.Status.MODULE_HANDOFF, profile.status());
		assertTrue(profile.owns(RESOURCE_PACK_INDEX));
		assertTrue(profile.owns(ZIP_READ_POOL));
		assertTrue(profile.owns(FONT_PROVIDER_PRESELECTION));
		assertTrue(profile.owns(ATLAS_MIP_PARALLEL));
		assertTrue(profile.owns(LOADING_FADE_CONTROL));
		assertTrue(profile.owns(LOADING_STATUS_OVERLAY));
	}
}
