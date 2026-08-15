package com.teenkung.packforge.config;

import com.teenkung.packforge.platform.OptionalModPresence;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityProfileReporterTest {
	@Test
	void absentProfileRequestKeepsReporterDormant() {
		assertTrue(CompatibilityProfileReporter.requestFrom(Map.of()).isEmpty());
	}

	@Test
	void requestIsValidatedDeduplicatedAndSorted() {
		var request = CompatibilityProfileReporter.requestFrom(Map.of(
			CompatibilityProfileReporter.PROFILE_ID_ENV, "fabric-quick-pack",
			CompatibilityProfileReporter.MOD_IDS_ENV, "quick-pack,immediatelyfast"
		)).orElseThrow();

		assertEquals("fabric-quick-pack", request.profileId());
		assertEquals(List.of("immediatelyfast", "quick-pack"), request.modIds());
		assertThrows(IllegalArgumentException.class, () -> CompatibilityProfileReporter.requestFrom(Map.of(
			CompatibilityProfileReporter.PROFILE_ID_ENV, "fabric-quick-pack",
			CompatibilityProfileReporter.MOD_IDS_ENV, "quick-pack,quick-pack"
		)));
		assertThrows(IllegalArgumentException.class, () -> CompatibilityProfileReporter.requestFrom(Map.of(
			CompatibilityProfileReporter.PROFILE_ID_ENV, "fabric-quick-pack",
			CompatibilityProfileReporter.MOD_IDS_ENV, "Quick Pack"
		)));
	}

	@Test
	void snapshotReportsLoaderObservedModsAndExactQuickPackPolicy() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.reloadOptimizerEnabled = true;
		config.largeAtlasFixerEnabled = true;
		config.loaderIndexEnabled = true;
		config.loaderZipPoolEnabled = true;
		config.loaderTimingsEnabled = true;
		config.reloadListenerTimingsEnabled = true;
		config.shaderApplyStallDiagnosticsEnabled = true;
		config.loadingScreenFadeOutDisabled = true;
		config.loadingStatusOverlayEnabled = true;
		config.reloadSummaryToastEnabled = true;
		config.fontPrepareProviderSelectionEnabled = true;
		config.fontBitmapProviderCacheEnabled = true;
		config.fontReloadDiagnosticsEnabled = true;
		config.atlasMipParallelEnabled = true;
		config.atlasDecodeBatchingEnabled = true;
		config.atlasCapEnabled = true;
		config.atlasRetryEnabled = true;
		config.startupOptimizerEnabled = true;
		config.startupTimingsEnabled = true;
		config.startupStatusOverlayEnabled = true;
		Properties properties = new Properties();
		properties.setProperty("target", "test-target");
		properties.setProperty("capabilities", String.join(",", Arrays.stream(PackForgeCapability.values()).map(Enum::name).toList()));
		FeaturePolicy policy = FeaturePolicy.forTesting(
			config,
			PackForgeCapabilityProfile.fromProperties(properties),
			QuickPackCompatibility.forTesting("1.5.0")
		);
		OptionalModPresence optionalMods = new OptionalModPresence() {
			@Override
			public boolean isModLoaded(String modId) {
				return modId.equals("quick-pack");
			}

			@Override
			public Optional<String> modVersion(String modId) {
				return modId.equals("quick-pack") ? Optional.of("1.5.0+1.21.1") : Optional.empty();
			}
		};
		var request = CompatibilityProfileReporter.requestFrom(Map.of(
			CompatibilityProfileReporter.PROFILE_ID_ENV, "fabric-quick-pack",
			CompatibilityProfileReporter.MOD_IDS_ENV, "quick-pack,immediatelyfast"
		)).orElseThrow();

		var snapshot = CompatibilityProfileReporter.capture(request, optionalMods, "fabric", "mc1_21_1", policy);
		String summary = snapshot.summary();

		assertTrue(summary.contains("mods=immediatelyfast:false:absent,quick-pack:true:1.5.0+1.21.1"));
		assertTrue(summary.contains("quickPackStatus=MODULE_HANDOFF"));
		assertEquals(
			EnumSet.of(
				PackForgeCapability.RESOURCE_PACK_INDEX,
				PackForgeCapability.FONT_PROVIDER_PRESELECTION,
				PackForgeCapability.ATLAS_MIP_PARALLEL,
				PackForgeCapability.LOADING_FADE_CONTROL
			).stream().map(Enum::name).sorted().toList(),
			snapshot.quickPackOwnedCapabilities()
		);
		assertEquals(EnumSet.of(
			PackForgeCapability.RESOURCE_PACK_INDEX,
			PackForgeCapability.FONT_PROVIDER_PRESELECTION,
			PackForgeCapability.ATLAS_MIP_PARALLEL,
			PackForgeCapability.LOADING_FADE_CONTROL
		).stream().map(Enum::name).collect(Collectors.toSet()), snapshot.overlap().keySet());
		assertEquals(PackForgeCapability.values().length - 4, snapshot.retained().size());
		assertTrue(snapshot.overlap().values().stream().noneMatch(Boolean::booleanValue));
		assertTrue(snapshot.retained().get("LOADER_TIMINGS"));
		assertTrue(snapshot.retained().get("ZIP_READ_POOL"));
		assertTrue(snapshot.retained().get("LOADING_STATUS_OVERLAY"));
		assertTrue(snapshot.retained().get("FONT_RELOAD_DIAGNOSTICS"));
		assertTrue(snapshot.retained().get("RELOAD_SUMMARY_TOAST"));
		assertTrue(snapshot.retained().get("STARTUP_STATUS_OVERLAY"));
		assertFalse(snapshot.mods().get(0).loaded());
		assertEquals(
			"id=fabric-quick-pack loader=fabric target=mc1_21_1 "
				+ "mods=immediatelyfast:false:absent,quick-pack:true:1.5.0+1.21.1 "
				+ "quickPackStatus=MODULE_HANDOFF "
				+ "quickPackOwns=ATLAS_MIP_PARALLEL,FONT_PROVIDER_PRESELECTION,LOADING_FADE_CONTROL,RESOURCE_PACK_INDEX "
				+ "overlap=ATLAS_MIP_PARALLEL:false,FONT_PROVIDER_PRESELECTION:false,LOADING_FADE_CONTROL:false,RESOURCE_PACK_INDEX:false "
				+ "retained=ATLAS_CAP:true,ATLAS_DECODE_BATCHING:true,ATLAS_PHASE_TIMINGS:false,ATLAS_RETRY:true,FONT_BITMAP_CACHE:true,FONT_RELOAD_DIAGNOSTICS:true,IMMEDIATELY_FAST_FONT_ATLAS_COMPAT:true,LOADER_TIMINGS:true,LOADING_STATUS_OVERLAY:true,MODEL_ADAPTIVE_BATCHING:false,MODEL_DUPLICATE_CACHE:false,MODEL_PARSE_BATCHING:true,MODEL_PARSE_TIMINGS:false,MODEL_UV_TRANSPARENCY_CLAMP:true,RELOAD_LISTENER_TIMINGS:true,RELOAD_SUMMARY_TOAST:true,SHADER_STALL_DIAGNOSTICS:true,STARTUP_ASYNC_CLASS_SCAN:false,STARTUP_ASYNC_DATA:false,STARTUP_ASYNC_FONT_ATLAS:false,STARTUP_EXECUTOR_TUNING:true,STARTUP_OPTIMIZER:true,STARTUP_STATUS_OVERLAY:true,STARTUP_TIMINGS:true,ZIP_READ_POOL:true",
			summary
		);
	}
}
