package com.teenkung.packforge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgeServices;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PackForgeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CURRENT_VERSION = 5;
	private static volatile Cfg INSTANCE;

	public static final class Cfg {
		public int configVersion = CURRENT_VERSION;
		public boolean reloadOptimizerEnabled = true;
		public boolean largeAtlasFixerEnabled = true;
		public boolean loaderIndexEnabled = true;
		public boolean loaderZipPoolEnabled = false;
		public boolean loaderTimingsEnabled = false;
		public boolean reloadListenerTimingsEnabled = false;
		public boolean loadingStatusOverlayEnabled = true;
		public boolean modelUvTransparencyClampEnabled = true;
		public boolean fontReloadDiagnosticsEnabled = false;
		public boolean fontPrepareProviderSelectionEnabled = true;
		public boolean fontBitmapProviderCacheEnabled = false;
		public boolean atlasPhaseTimingsEnabled = false;
		public boolean atlasMipParallelEnabled = false;
		public int atlasMipBatchSize = 128;
		public boolean modelParseBatchingEnabled = true;
		public int modelParseBatchSize = 64;
		public boolean modelDuplicateParseCacheEnabled = false;
		public boolean atlasCapEnabled = true;
		public int atlasCapPx = 256;
		public List<String> atlasExcludeIds = new ArrayList<>(List.of("minecraft:gui"));
		public boolean atlasRetryEnabled = false;
		public int atlasRetryMaxAttempts = 2;
		public boolean forceDisablePartIIIWithIris = true;
		public boolean experimentalAtlasSplit = false;
		public List<String> atlasSplitTargets = new ArrayList<>(List.of("minecraft:items", "minecraft:particles"));
		public int atlasSplitMaxTiers = 1;
		public boolean atlasSplitFallbackToDownscale = true;
		public boolean atlasSplitDisableWithIris = true;
		public boolean atlasSplitDisableWithSodium = false;
		public boolean atlasSplitModelCoherence = true;
		public boolean atlasSplitDiagnostics = true;
	}

	public static Cfg get() {
		Cfg c = INSTANCE;
		return c != null ? c : new Cfg();
	}

	public static synchronized void load() {
		Path file = configFile();
		Cfg cfg = new Cfg();
		boolean shouldSave = false;
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file)) {
					JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
					Cfg parsed = GSON.fromJson(root, Cfg.class);
					if (parsed != null) {
						cfg = parsed;
					}
					shouldSave = applyMissingDefaults(cfg, root);
				}
			} else {
				shouldSave = true;
			}
		} catch (IOException e) {
			PackForge.LOGGER.error("Failed to load packforge config; using defaults", e);
		} catch (RuntimeException e) {
			PackForge.LOGGER.error("Failed to parse packforge config; using defaults", e);
			shouldSave = true;
		}
		shouldSave |= sanitize(cfg);
		INSTANCE = cfg;
		if (shouldSave) {
			save();
		}
	}

	public static synchronized void save() {
		Cfg cfg = get();
		cfg.configVersion = CURRENT_VERSION;
		sanitize(cfg);
		Path file = configFile();
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file)) {
				GSON.toJson(cfg, w);
			}
		} catch (IOException e) {
			PackForge.LOGGER.error("Failed to save packforge config", e);
		}
	}

	private static Path configFile() {
		return PackForgeServices.platform().configDirectory().resolve("packforge.json");
	}

	private static boolean applyMissingDefaults(Cfg cfg, JsonObject root) {
		Cfg defaults = new Cfg();
		boolean changed = false;

		if (!root.has("configVersion") || cfg.configVersion != CURRENT_VERSION) {
			cfg.configVersion = CURRENT_VERSION;
			changed = true;
		}
		if (!root.has("loaderIndexEnabled")) { cfg.loaderIndexEnabled = defaults.loaderIndexEnabled; changed = true; }
		if (!root.has("reloadOptimizerEnabled")) { cfg.reloadOptimizerEnabled = defaults.reloadOptimizerEnabled; changed = true; }
		if (!root.has("largeAtlasFixerEnabled")) { cfg.largeAtlasFixerEnabled = defaults.largeAtlasFixerEnabled; changed = true; }
		if (!root.has("loaderZipPoolEnabled")) { cfg.loaderZipPoolEnabled = defaults.loaderZipPoolEnabled; changed = true; }
		if (!root.has("loaderTimingsEnabled")) { cfg.loaderTimingsEnabled = defaults.loaderTimingsEnabled; changed = true; }
		if (!root.has("reloadListenerTimingsEnabled")) { cfg.reloadListenerTimingsEnabled = defaults.reloadListenerTimingsEnabled; changed = true; }
		if (!root.has("loadingStatusOverlayEnabled")) { cfg.loadingStatusOverlayEnabled = defaults.loadingStatusOverlayEnabled; changed = true; }
		if (!root.has("modelUvTransparencyClampEnabled")) { cfg.modelUvTransparencyClampEnabled = defaults.modelUvTransparencyClampEnabled; changed = true; }
		if (!root.has("fontReloadDiagnosticsEnabled")) { cfg.fontReloadDiagnosticsEnabled = defaults.fontReloadDiagnosticsEnabled; changed = true; }
		if (!root.has("fontPrepareProviderSelectionEnabled")) { cfg.fontPrepareProviderSelectionEnabled = defaults.fontPrepareProviderSelectionEnabled; changed = true; }
		if (!root.has("fontBitmapProviderCacheEnabled")) { cfg.fontBitmapProviderCacheEnabled = defaults.fontBitmapProviderCacheEnabled; changed = true; }
		if (!root.has("atlasPhaseTimingsEnabled")) { cfg.atlasPhaseTimingsEnabled = defaults.atlasPhaseTimingsEnabled; changed = true; }
		if (!root.has("atlasMipParallelEnabled")) { cfg.atlasMipParallelEnabled = defaults.atlasMipParallelEnabled; changed = true; }
		if (!root.has("atlasMipBatchSize")) { cfg.atlasMipBatchSize = defaults.atlasMipBatchSize; changed = true; }
		if (!root.has("modelParseBatchingEnabled")) { cfg.modelParseBatchingEnabled = defaults.modelParseBatchingEnabled; changed = true; }
		if (!root.has("modelParseBatchSize")) { cfg.modelParseBatchSize = defaults.modelParseBatchSize; changed = true; }
		if (!root.has("modelDuplicateParseCacheEnabled")) { cfg.modelDuplicateParseCacheEnabled = defaults.modelDuplicateParseCacheEnabled; changed = true; }
		if (!root.has("atlasCapEnabled")) { cfg.atlasCapEnabled = defaults.atlasCapEnabled; changed = true; }
		if (!root.has("atlasCapPx")) { cfg.atlasCapPx = defaults.atlasCapPx; changed = true; }
		if (!root.has("atlasExcludeIds") || cfg.atlasExcludeIds == null) { cfg.atlasExcludeIds = defaults.atlasExcludeIds; changed = true; }
		if (!root.has("atlasRetryEnabled")) { cfg.atlasRetryEnabled = defaults.atlasRetryEnabled; changed = true; }
		if (!root.has("atlasRetryMaxAttempts")) { cfg.atlasRetryMaxAttempts = defaults.atlasRetryMaxAttempts; changed = true; }
		if (!root.has("forceDisablePartIIIWithIris")) { cfg.forceDisablePartIIIWithIris = defaults.forceDisablePartIIIWithIris; changed = true; }
		if (!root.has("experimentalAtlasSplit")) { cfg.experimentalAtlasSplit = defaults.experimentalAtlasSplit; changed = true; }
		if (!root.has("atlasSplitTargets") || cfg.atlasSplitTargets == null) { cfg.atlasSplitTargets = defaults.atlasSplitTargets; changed = true; }
		if (!root.has("atlasSplitMaxTiers")) { cfg.atlasSplitMaxTiers = defaults.atlasSplitMaxTiers; changed = true; }
		if (!root.has("atlasSplitFallbackToDownscale")) { cfg.atlasSplitFallbackToDownscale = defaults.atlasSplitFallbackToDownscale; changed = true; }
		if (!root.has("atlasSplitDisableWithIris")) { cfg.atlasSplitDisableWithIris = defaults.atlasSplitDisableWithIris; changed = true; }
		if (!root.has("atlasSplitDisableWithSodium")) { cfg.atlasSplitDisableWithSodium = defaults.atlasSplitDisableWithSodium; changed = true; }
		if (!root.has("atlasSplitModelCoherence")) { cfg.atlasSplitModelCoherence = defaults.atlasSplitModelCoherence; changed = true; }
		if (!root.has("atlasSplitDiagnostics")) { cfg.atlasSplitDiagnostics = defaults.atlasSplitDiagnostics; changed = true; }

		cfg.atlasCapPx = clamp(cfg.atlasCapPx, 16, 8192);
		cfg.atlasRetryMaxAttempts = clamp(cfg.atlasRetryMaxAttempts, 1, 10);
		cfg.atlasMipBatchSize = clamp(cfg.atlasMipBatchSize, 16, 4096);
		cfg.modelParseBatchSize = clamp(cfg.modelParseBatchSize, 8, 1024);
		cfg.atlasSplitMaxTiers = clamp(cfg.atlasSplitMaxTiers, 1, 4);
		return changed;
	}

	private static boolean sanitize(Cfg cfg) {
		boolean changed = false;
		if (cfg.atlasExcludeIds == null) {
			cfg.atlasExcludeIds = new ArrayList<>(new Cfg().atlasExcludeIds);
			changed = true;
		}
		if (cfg.atlasSplitTargets == null) {
			cfg.atlasSplitTargets = new ArrayList<>(new Cfg().atlasSplitTargets);
			changed = true;
		}
		ArrayList<String> safeTargets = new ArrayList<>();
		for (String target : cfg.atlasSplitTargets) {
			if (target == null) {
				changed = true;
				continue;
			}
			String normalized = target.trim().toLowerCase();
			if (normalized.isEmpty() || normalized.equals("minecraft:blocks")) {
				changed = true;
				continue;
			}
			if ((normalized.equals("minecraft:items") || normalized.equals("minecraft:particles")) && !safeTargets.contains(normalized)) {
				safeTargets.add(normalized);
			} else {
				changed = true;
			}
		}
		if (!cfg.atlasSplitTargets.equals(safeTargets)) {
			cfg.atlasSplitTargets = safeTargets;
			changed = true;
		}
		int oldTiers = cfg.atlasSplitMaxTiers;
		cfg.atlasSplitMaxTiers = clamp(cfg.atlasSplitMaxTiers, 1, 4);
		return changed || oldTiers != cfg.atlasSplitMaxTiers;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private PackForgeConfig() {}
}
