package com.teenkung.packforge.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teenkung.packforge.platform.PackForgePlatform;
import com.teenkung.packforge.platform.PackForgeServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipReadPoolDefaultPromotionTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void missingFieldAdoptsPromotedDefault() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		Path configFile = temporaryDirectory.resolve("packforge.json");
		Files.writeString(configFile, "{\"configVersion\":12,\"loaderIndexEnabled\":true}");

		PackForgeConfig.load();

		assertTrue(PackForgeConfig.get().loaderZipPoolEnabled);
		JsonObject saved = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
		assertTrue(saved.get("loaderZipPoolEnabled").getAsBoolean());
	}

	@Test
	void explicitFalseRemainsDisabled() throws Exception {
		PackForgeServices.init(new TestPlatform(temporaryDirectory));
		Path configFile = temporaryDirectory.resolve("packforge.json");
		Files.writeString(configFile, "{\"configVersion\":12,\"loaderIndexEnabled\":true,\"loaderZipPoolEnabled\":false}");

		PackForgeConfig.load();
		assertFalse(PackForgeConfig.get().loaderZipPoolEnabled);
		PackForgeConfig.save();

		JsonObject saved = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
		assertFalse(saved.get("loaderZipPoolEnabled").getAsBoolean());
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
