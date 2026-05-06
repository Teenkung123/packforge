package com.teenkung.packforge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teenkung.packforge.PackForge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PackForgeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile Cfg INSTANCE;

	public static final class Cfg {
		public boolean loaderIndexEnabled = true;
		public boolean loaderZipPoolEnabled = false;
		public boolean loaderTimingsEnabled = false;
		public boolean atlasCapEnabled = true;
		public int atlasCapPx = 256;
		public List<String> atlasExcludeIds = new ArrayList<>(List.of("minecraft:gui"));
		public boolean atlasRetryEnabled = false;
		public int atlasRetryMaxAttempts = 2;
		public boolean forceDisablePartIIIWithIris = true;
	}

	public static Cfg get() {
		Cfg c = INSTANCE;
		return c != null ? c : new Cfg();
	}

	public static synchronized void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("packforge.json");
		Cfg cfg = new Cfg();
		try {
			if (Files.exists(file)) {
				try (var r = Files.newBufferedReader(file)) {
					Cfg parsed = GSON.fromJson(r, Cfg.class);
					if (parsed != null) cfg = parsed;
				}
			} else {
				Files.createDirectories(file.getParent());
				try (var w = Files.newBufferedWriter(file)) {
					GSON.toJson(cfg, w);
				}
			}
		} catch (IOException e) {
			PackForge.LOGGER.error("Failed to load packforge config; using defaults", e);
		}
		INSTANCE = cfg;
	}

	private PackForgeConfig() {}
}
