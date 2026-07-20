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

	private record TestPlatform(Path configDirectory) implements PackForgePlatform {
		@Override public String loaderName() { return "test"; }
		@Override public boolean isModLoaded(String modId) { return false; }
		@Override public Path gameDirectory() { return configDirectory; }
		@Override public boolean isDevelopmentEnvironment() { return true; }
		@Override public Executor backgroundExecutor() { return Runnable::run; }
		@Override public void logPlatformInfo() {}
	}
}
