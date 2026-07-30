package com.teenkung.packforge.config;

import com.teenkung.packforge.platform.PackForgePlatform;
import com.teenkung.packforge.platform.PackForgeServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeConfigPreservationTest {
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
		assertTrue(saved.contains("\"configVersion\": 12"));
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

	private record TestPlatform(Path configDirectory) implements PackForgePlatform {
		@Override public String loaderName() { return "test"; }
		@Override public boolean isModLoaded(String modId) { return false; }
		@Override public Path gameDirectory() { return configDirectory; }
		@Override public boolean isDevelopmentEnvironment() { return true; }
		@Override public Executor backgroundExecutor() { return Runnable::run; }
		@Override public void logPlatformInfo() {}
	}
}
