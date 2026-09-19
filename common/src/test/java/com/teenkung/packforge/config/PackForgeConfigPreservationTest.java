package com.teenkung.packforge.config;

import com.teenkung.packforge.platform.PackForgePlatform;
import com.teenkung.packforge.platform.PackForgeServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeConfigPreservationTest {
	@Test
	void backportDoesNotActivateAnInertLegacyCpuAlias() {
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		config.largeAtlasFixerEnabled = true;
		config.atlasMipParallelEnabled = true;
		Set<PackForgeCapability> available = EnumSet.allOf(PackForgeCapability.class);
		assertFalse(PackForgeConfig.legacyCpuMipPreparation(config, available, "mc1_21_11"));
		assertTrue(PackForgeConfig.legacyCpuMipPreparation(config, available, "mc26_1_to_26_2"));
	}

	@TempDir
	Path temporaryDirectory;

	@Test
	void preservesStoredValuesForCapabilitiesThatMayBeUnavailable() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		Path configFile = temporaryDirectory.resolve("packforge.json");
		Files.writeString(configFile, """
			{
			  "configVersion": 12,
			  "reloadOptimizerEnabled": false,
			  "loaderIndexEnabled": false,
			  "loaderZipPoolEnabled": true,
			  "loaderTimingsEnabled": true,
			  "atlasCapEnabled": false,
			  "atlasCapPx": 1024,
			  "atlasRetryEnabled": true,
			  "fontPrepareProviderSelectionEnabled": false,
			  "modelParseBatchingEnabled": false,
			  "experimentalAtlasSplit": true,
			  "startupOptimizerEnabled": true,
			  "startupAsyncDataParsingEnabled": true
			}
			""");

		PackForgeConfig.load();
		PackForgeConfig.Cfg loaded = PackForgeConfig.get();
		assertFalse(loaded.reloadOptimizerEnabled);
		assertFalse(loaded.loaderIndexEnabled);
		assertTrue(loaded.loaderZipPoolEnabled);
		assertTrue(loaded.loaderTimingsEnabled);
		assertFalse(loaded.atlasCapEnabled);
		assertEquals(1024, loaded.atlasCapPx);
		assertTrue(loaded.atlasRetryEnabled);
		assertFalse(loaded.fontPrepareProviderSelectionEnabled);
		assertFalse(loaded.modelParseBatchingEnabled);
		assertTrue(loaded.experimentalAtlasSplit);
		assertTrue(loaded.startupOptimizerEnabled);
		assertTrue(loaded.startupAsyncDataParsingEnabled);

		String saved = Files.readString(configFile);
		assertTrue(saved.contains("\"configVersion\": 15"));
		assertTrue(saved.contains("\"atlasCapEnabled\": false"));
		assertTrue(saved.contains("\"atlasRetryEnabled\": true"));
		assertTrue(saved.contains("\"experimentalAtlasSplit\": true"));
		assertTrue(saved.contains("\"startupAsyncDataParsingEnabled\": true"));
	}

	@Test
	void applyAndSaveInstallsDetachedCopyAfterSuccessfulAtomicWrite() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		PackForgeConfig.load();
		PackForgeConfig.Cfg draft = PackForgeConfig.copyOf(PackForgeConfig.get());
		draft.loaderIndexEnabled = false;
		draft.atlasExcludeIds.add("example:test");

		PackForgeConfig.SaveResult result = PackForgeConfig.applyAndSave(draft);

		assertTrue(result.successful(), result.errorMessage());
		assertFalse(PackForgeConfig.get().loaderIndexEnabled);
		assertTrue(PackForgeConfig.get().atlasExcludeIds.contains("example:test"));
		draft.atlasExcludeIds.add("example:after-save");
		assertFalse(PackForgeConfig.get().atlasExcludeIds.contains("example:after-save"));
		String saved = Files.readString(temporaryDirectory.resolve("packforge.json"));
		assertTrue(saved.contains("\"loaderIndexEnabled\": false"));
		assertTrue(saved.contains("\"example:test\""));
	}

	@Test
	void failedSaveDoesNotInstallDraftAsLiveConfiguration() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		PackForgeConfig.load();
		PackForgeConfig.Cfg liveBeforeFailure = PackForgeConfig.get();
		PackForgeConfig.Cfg draft = PackForgeConfig.copyOf(liveBeforeFailure);
		draft.loaderIndexEnabled = !liveBeforeFailure.loaderIndexEnabled;

		Path blockingFile = temporaryDirectory.resolve("not-a-directory");
		Files.writeString(blockingFile, "blocks config directory creation");
		PackForgeServices.init(new TestPlatform(blockingFile));
		PackForgeConfig.SaveResult result = PackForgeConfig.applyAndSave(draft);

		assertFalse(result.successful());
		assertEquals(liveBeforeFailure.loaderIndexEnabled, PackForgeConfig.get().loaderIndexEnabled);
	}

	@Test
	void migrationBacksUpExactBytesAndRetainsUnknownFieldsAndExplicitValues() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		Path file = temporaryDirectory.resolve("packforge.json");
		String original = "{\r\n  \"configVersion\": 12, \"resourceReadReuseEnabled\": true, \"optimizationMemoryMiB\": 48, \"futureSetting\": {\"nested\": 7}\r\n}\r\n";
		Files.writeString(file, original);
		Files.writeString(temporaryDirectory.resolve("packforge-v12-existing.json.bak"), "older backup");
		PackForgeConfig.load();
		assertTrue(PackForgeConfig.get().resourceReadReuseEnabled);
		assertEquals(48, PackForgeConfig.get().optimizationMemoryMiB);
		assertEquals(15, PackForgeConfig.get().configVersion);
		assertTrue(Files.readString(file).contains("\"futureSetting\""));
		try (var files = Files.list(temporaryDirectory)) {
			var backups = files.filter(path -> path.toString().endsWith(".bak") && !path.getFileName().toString().contains("existing")).toList();
			assertEquals(1, backups.size());
			assertArrayEquals(original.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(backups.get(0)));
		}
		assertEquals("older backup", Files.readString(temporaryDirectory.resolve("packforge-v12-existing.json.bak")));
		assertTrue(PackForgeConfig.applyAndSave(PackForgeConfig.copyOf(PackForgeConfig.get())).successful());
		assertTrue(Files.readString(file).contains("\"futureSetting\""));
	}

	@Test
	void malformedConfigIsNotOverwrittenByLoadOrSave() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		Path file = temporaryDirectory.resolve("packforge.json");
		String original = "{ invalid unfinished json";
		Files.writeString(file, original);
		PackForgeConfig.load();
		assertFalse(PackForgeConfig.get().resourceReadReuseEnabled);
		assertEquals(128, PackForgeConfig.get().optimizationMemoryMiB);
		PackForgeConfig.save();
		assertFalse(PackForgeConfig.applyAndSave(new PackForgeConfig.Cfg()).successful());
		assertEquals(original, Files.readString(file));
	}

	@Test
	void wrongTypedKnownFieldAlsoSurvivesExplicitSave() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		Path file = temporaryDirectory.resolve("packforge.json");
		String original = "{\"configVersion\": 12, \"optimizationMemoryMiB\": \"invalid\"}";
		Files.writeString(file, original);
		PackForgeConfig.load();
		assertFalse(PackForgeConfig.applyAndSave(new PackForgeConfig.Cfg()).successful());
		assertEquals(original, Files.readString(file));
	}

	@Test
	void futureConfigIsNeverDowngradedEvenByExplicitSave() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		Path file = temporaryDirectory.resolve("packforge.json");
		String original = "{\"configVersion\": 999, \"reloadOptimizerEnabled\": false, \"optimizationMemoryMiB\": 999, \"newSetting\": 1}";
		Files.writeString(file, original);
		PackForgeConfig.load();
		assertEquals(999, PackForgeConfig.get().configVersion);
		assertFalse(PackForgeConfig.get().reloadOptimizerEnabled);
		assertEquals(128, PackForgeConfig.get().optimizationMemoryMiB);
		PackForgeConfig.save();
		assertFalse(PackForgeConfig.applyAndSave(new PackForgeConfig.Cfg()).successful());
		assertEquals(original, Files.readString(file));
	}

	@Test
	void malformedKnownTypesCannotBeCoercedAndOverwrittenDuringMigration() throws Exception {
		for (String badField : new String[] {
			"\"cpuMipPreparationEnabled\":\"yes\"", "\"fontBitmapProviderCacheEnabled\":null",
			"\"atlasExcludeIds\":[7]", "\"optimizationMemoryMiB\":1.5", "\"configVersion\":\"13\""
		}) {
			Path directory = Files.createTempDirectory(temporaryDirectory, "invalid-field-");
			PackForgeServices.init(new TestPlatform(directory));
			Path file = directory.resolve("packforge.json");
			String original = "{" + badField + "}";
			Files.writeString(file, original);
			PackForgeConfig.load();
			assertFalse(PackForgeConfig.applyAndSave(new PackForgeConfig.Cfg()).successful());
			assertEquals(original, Files.readString(file));
			try (var paths = Files.list(directory)) {
				assertEquals(1, paths.count());
			}
		}
	}

	@Test
	void memoryBudgetIsClampedCopiedAndCapturedAtReloadBoundary() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		PackForgeConfig.load();
		PackForgeConfig.Cfg draft = PackForgeConfig.copyOf(PackForgeConfig.get());
		draft.optimizationMemoryMiB = -1;
		assertTrue(PackForgeConfig.applyAndSave(draft).successful());
		assertEquals(1, PackForgeConfig.get().optimizationMemoryMiB);
		ReloadFeatureSnapshot snapshot = ReloadFeatureSnapshot.capture();
		draft.optimizationMemoryMiB = 256;
		draft.resourceReadReuseEnabled = true;
		draft.reloadOptimizerEnabled = false;
		assertTrue(PackForgeConfig.applyAndSave(draft).successful());
		assertEquals(128, PackForgeConfig.get().optimizationMemoryMiB);
		assertTrue(PackForgeConfig.copyOf(PackForgeConfig.get()).resourceReadReuseEnabled);
		assertFalse(FeatureFlags.resourceReadReuseEnabled());
		assertEquals(1, snapshot.optimizationMemoryMiB());
		assertFalse(snapshot.resourceReadReuseEnabled());
	}

	@Test
	void oldMipAliasPreservesEveryMasterSwitchCombination() throws Exception {
		for (boolean atlasMaster : new boolean[] {false, true}) {
			for (boolean mipAlias : new boolean[] {false, true}) {
				for (boolean startupMaster : new boolean[] {false, true}) {
					for (boolean startupAsync : new boolean[] {false, true}) {
						Path directory = Files.createTempDirectory(temporaryDirectory, "mip-migration-");
						PackForgeServices.init(new TestPlatform(directory));
						String original = "{\"configVersion\":13,\"largeAtlasFixerEnabled\":" + atlasMaster
							+ ",\"atlasMipParallelEnabled\":" + mipAlias + ",\"startupOptimizerEnabled\":" + startupMaster
							+ ",\"startupAsyncFontAtlasEnabled\":" + startupAsync + "}";
						Path file = directory.resolve("packforge.json");
						Files.writeString(file, original);
						PackForgeConfig.load();
						PackForgeConfig.Cfg cfg = PackForgeConfig.get();
						boolean expected = PackForgeCapabilities.supports(PackForgeCapability.ATLAS_MIP_PARALLEL)
							&& ((atlasMaster && mipAlias) || (startupMaster && startupAsync
								&& PackForgeCapabilities.supports(PackForgeCapability.STARTUP_OPTIMIZER)
								&& PackForgeCapabilities.supports(PackForgeCapability.STARTUP_ASYNC_FONT_ATLAS)));
						assertEquals(expected, cfg.cpuMipPreparationEnabled);
						assertEquals(mipAlias, cfg.atlasMipParallelEnabled);
						assertEquals(atlasMaster, cfg.largeAtlasFixerEnabled);
						assertEquals(cfg.cpuMipPreparationEnabled, PackForgeConfig.copyOf(cfg).cpuMipPreparationEnabled);
						try (var paths = Files.list(directory)) {
							var backups = paths.filter(path -> path.toString().endsWith(".bak")).toList();
							assertEquals(1, backups.size());
							assertEquals(original, Files.readString(backups.get(0)));
						}
					}
				}
			}
		}
	}

	@Test
	void legacyMipMigrationRequiresEveryOriginalCapabilityAndMasterGate() {
		for (int capabilityBits = 0; capabilityBits < 8; capabilityBits++) {
			Set<PackForgeCapability> capabilities = EnumSet.noneOf(PackForgeCapability.class);
			boolean mipSupported = (capabilityBits & 1) != 0;
			boolean startupSupported = (capabilityBits & 2) != 0;
			boolean fontAsyncSupported = (capabilityBits & 4) != 0;
			if (mipSupported) capabilities.add(PackForgeCapability.ATLAS_MIP_PARALLEL);
			if (startupSupported) capabilities.add(PackForgeCapability.STARTUP_OPTIMIZER);
			if (fontAsyncSupported) capabilities.add(PackForgeCapability.STARTUP_ASYNC_FONT_ATLAS);
			for (int settingBits = 0; settingBits < 16; settingBits++) {
				PackForgeConfig.Cfg cfg = new PackForgeConfig.Cfg();
				cfg.largeAtlasFixerEnabled = (settingBits & 1) != 0;
				cfg.atlasMipParallelEnabled = (settingBits & 2) != 0;
				cfg.startupOptimizerEnabled = (settingBits & 4) != 0;
				cfg.startupAsyncFontAtlasEnabled = (settingBits & 8) != 0;
				boolean atlasRequestActive = (settingBits & 3) == 3;
				boolean startupRequestActive = (settingBits & 12) == 12;
				assertEquals(mipSupported && (atlasRequestActive || (startupSupported && fontAsyncSupported && startupRequestActive)),
					PackForgeConfig.legacyCpuMipPreparation(cfg, capabilities),
					"capability mask " + capabilityBits + ", setting mask " + settingBits);
			}
		}
	}

	@Test
	void unavailableLegacyMipRequestsDoNotBecomeIndependentRequestsWhenCopied() {
		PackForgeConfig.Cfg legacy = new PackForgeConfig.Cfg();
		legacy.atlasMipParallelEnabled = true;
		legacy.startupOptimizerEnabled = true;
		legacy.startupAsyncFontAtlasEnabled = true;
		legacy.cpuMipPreparationEnabled = PackForgeConfig.legacyCpuMipPreparation(legacy,
			Set.of(PackForgeCapability.RESOURCE_PACK_INDEX));
		PackForgeConfig.Cfg copied = PackForgeConfig.copyOf(legacy);
		assertFalse(copied.cpuMipPreparationEnabled);
		assertTrue(copied.atlasMipParallelEnabled);
		assertTrue(copied.startupAsyncFontAtlasEnabled);
		assertTrue(PackForgeConfig.legacyCpuMipPreparation(copied, EnumSet.allOf(PackForgeCapability.class)));
	}

	@Test
	void explicitCpuMipChoiceOverridesLegacyAliasesWithoutChangingThem() throws Exception {
		for (boolean canonical : new boolean[] {false, true}) {
			Path directory = Files.createTempDirectory(temporaryDirectory, "canonical-");
			PackForgeServices.init(new TestPlatform(directory));
			Files.writeString(directory.resolve("packforge.json"), "{\"configVersion\":13,\"cpuMipPreparationEnabled\":"
				+ canonical + ",\"largeAtlasFixerEnabled\":true,\"atlasMipParallelEnabled\":true,\"futureChoice\":42}");
			PackForgeConfig.load();
			assertEquals(canonical, PackForgeConfig.get().cpuMipPreparationEnabled);
			assertTrue(PackForgeConfig.get().atlasMipParallelEnabled);
			assertTrue(Files.readString(directory.resolve("packforge.json")).contains("\"futureChoice\": 42"));
		}
	}

	@Test
	void configV14MigrationBacksUpAndPreservesExplicitAtlasCapChoice() throws Exception {
		Path explicitDirectory = Files.createTempDirectory(temporaryDirectory, "atlas-cap-explicit-");
		PackForgeServices.init(new TestPlatform(explicitDirectory));
		String explicit = "{\"configVersion\":14,\"atlasCapEnabled\":true,\"atlasCapPx\":512}";
		Files.writeString(explicitDirectory.resolve("packforge.json"), explicit);

		PackForgeConfig.load();

		assertEquals(15, PackForgeConfig.get().configVersion);
		assertTrue(PackForgeConfig.get().atlasCapEnabled);
		assertEquals(512, PackForgeConfig.get().atlasCapPx);
		try (var paths = Files.list(explicitDirectory)) {
			var backups = paths.filter(path -> path.toString().endsWith(".bak")).toList();
			assertEquals(1, backups.size());
			assertEquals(explicit, Files.readString(backups.get(0)));
		}

		Path defaultDirectory = Files.createTempDirectory(temporaryDirectory, "atlas-cap-default-");
		PackForgeServices.init(new TestPlatform(defaultDirectory));
		Files.writeString(defaultDirectory.resolve("packforge.json"), "{\"configVersion\":14}");

		PackForgeConfig.load();

		assertEquals(15, PackForgeConfig.get().configVersion);
		assertFalse(PackForgeConfig.get().atlasCapEnabled);
	}

	@Test
	void newConfigurationEnablesCpuPreparationWithoutDiagnosticOrExecutorTuningDefaults() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		PackForgeConfig.load();
		PackForgeConfig.Cfg cfg = PackForgeConfig.get();
		assertTrue(cfg.cpuMipPreparationEnabled);
		assertFalse(cfg.atlasMipParallelEnabled);
		assertFalse(cfg.shaderApplyStallDiagnosticsEnabled);
		assertFalse(cfg.startupTimingsEnabled);
		assertFalse(cfg.startupExecutorTuningEnabled);
		assertFalse(cfg.fontBitmapProviderCacheEnabled);
		assertFalse(cfg.atlasCapEnabled);
		assertFalse(cfg.loaderZipPoolEnabled);
		assertFalse(cfg.resourceReadReuseEnabled);
	}

	private record TestPlatform(Path configDirectory) implements PackForgePlatform {
		@Override public String loaderName() { return "test"; }
		@Override public boolean isModLoaded(String modId) { return false; }
		@Override public Path gameDirectory() { return configDirectory; }
		@Override public boolean isDevelopmentEnvironment() { return true; }
		@Override public Executor backgroundExecutor() { return Runnable::run; }
		@Override public void logPlatformInfo() {}
	}
}
