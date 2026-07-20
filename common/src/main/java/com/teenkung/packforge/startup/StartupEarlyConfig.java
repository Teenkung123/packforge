package com.teenkung.packforge.startup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

public final class StartupEarlyConfig {
	private static volatile Settings settings;
	private static volatile Boolean smoothBootPresent;

	public static Settings get() {
		Settings cached = settings;
		if (cached != null) {
			return cached;
		}
		Settings loaded = load();
		settings = loaded;
		return loaded;
	}

	public static boolean isLikelySmoothBootPresent() {
		Boolean cached = smoothBootPresent;
		if (cached != null) {
			return cached;
		}
		boolean present = scanModsFolder();
		smoothBootPresent = present;
		return present;
	}

	private static Settings load() {
		Path file = Path.of("config", "packforge.json");
		if (!Files.exists(file)) {
			return Settings.defaults();
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			Settings defaults = Settings.defaults();
			return new Settings(
				booleanValue(root, "startupOptimizerEnabled", defaults.startupOptimizerEnabled),
				booleanValue(root, "startupTimingsEnabled", defaults.startupTimingsEnabled),
				booleanValue(root, "startupStatusOverlayEnabled", defaults.startupStatusOverlayEnabled),
				booleanValue(root, "startupExecutorTuningEnabled", defaults.startupExecutorTuningEnabled),
				intValue(root, "startupWorkerThreads", defaults.startupWorkerThreads),
				intValue(root, "startupThreadPriority", defaults.startupThreadPriority),
				booleanValue(root, "startupSkipWithSmoothBoot", defaults.startupSkipWithSmoothBoot)
			);
		} catch (IOException | RuntimeException ignored) {
			return Settings.defaults();
		}
	}

	private static boolean scanModsFolder() {
		Path mods = Path.of("mods");
		if (!Files.isDirectory(mods)) {
			return false;
		}
		try (Stream<Path> paths = Files.list(mods)) {
			return paths.map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
				.anyMatch(name -> name.contains("smoothboot"));
		} catch (IOException ignored) {
			return false;
		}
	}

	private static boolean booleanValue(JsonObject root, String name, boolean defaultValue) {
		return root.has(name) ? root.get(name).getAsBoolean() : defaultValue;
	}

	private static int intValue(JsonObject root, String name, int defaultValue) {
		return root.has(name) ? root.get(name).getAsInt() : defaultValue;
	}

	public record Settings(
		boolean startupOptimizerEnabled,
		boolean startupTimingsEnabled,
		boolean startupStatusOverlayEnabled,
		boolean startupExecutorTuningEnabled,
		int startupWorkerThreads,
		int startupThreadPriority,
		boolean startupSkipWithSmoothBoot
	) {
		static Settings defaults() {
			return new Settings(false, true, true, true, 0, 4, true);
		}
	}

	private StartupEarlyConfig() {}
}
