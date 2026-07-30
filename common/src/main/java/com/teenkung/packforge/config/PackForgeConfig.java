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
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class PackForgeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CURRENT_VERSION = 12;
	private static volatile Cfg INSTANCE;

	public static final class Cfg {
		public int configVersion = CURRENT_VERSION;
		public boolean reloadOptimizerEnabled = true;
		public boolean largeAtlasFixerEnabled = true;
		public boolean loaderIndexEnabled = true;
		public boolean loaderZipPoolEnabled = false;
		public boolean loaderTimingsEnabled = false;
		public boolean reloadListenerTimingsEnabled = false;
		public boolean shaderApplyStallDiagnosticsEnabled = true;
		public boolean immediatelyFastFontAtlasCompatEnabled = true;
		public boolean loadingStatusOverlayEnabled = true;
		public boolean loadingScreenFadeOutDisabled = false;
		public boolean reloadSummaryToastEnabled = false;
		public boolean modelUvTransparencyClampEnabled = true;
		public boolean fontReloadDiagnosticsEnabled = false;
		public boolean fontPrepareProviderSelectionEnabled = true;
		public boolean fontBitmapProviderCacheEnabled = false;
		public boolean atlasPhaseTimingsEnabled = false;
		public boolean atlasMipParallelEnabled = false;
		public int atlasMipBatchSize = 128;
		public boolean atlasDecodeBatchingEnabled = false;
		public int atlasDecodeBatchSize = 128;
		public boolean modelParseBatchingEnabled = true;
		public int modelParseBatchSize = 64;
		public boolean modelParseTimingEnabled = false;
		public boolean modelAdaptiveBatchingEnabled = false;
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
		public boolean startupOptimizerEnabled = false;
		public boolean startupTimingsEnabled = true;
		public boolean startupStatusOverlayEnabled = true;
		public boolean startupExecutorTuningEnabled = true;
		public int startupWorkerThreads = 0;
		public int startupThreadPriority = 4;
		public boolean startupSkipWithSmoothBoot = true;
		public boolean startupAsyncDataParsingEnabled = false;
		public boolean startupAsyncClassScanEnabled = false;
		public boolean startupAsyncFontAtlasEnabled = false;
	}

	public record SaveResult(boolean successful, String errorMessage) {
		private static SaveResult success() {
			return new SaveResult(true, "");
		}

		private static SaveResult failure(IOException exception) {
			String message = exception.getMessage();
			return new SaveResult(false, message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
		}
	}

	public static Cfg get() {
		Cfg c = INSTANCE;
		return c != null ? c : new Cfg();
	}

	public static boolean isLoaded() {
		return INSTANCE != null;
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
		write(cfg);
	}

	/**
	 * Atomically persists a detached configuration and installs it as the live
	 * configuration only after the write succeeds.
	 */
	public static synchronized SaveResult applyAndSave(Cfg replacement) {
		Cfg candidate = copyOf(replacement);
		candidate.configVersion = CURRENT_VERSION;
		sanitize(candidate);
		SaveResult result = write(candidate);
		if (result.successful()) {
			INSTANCE = candidate;
		}
		return result;
	}

	public static Cfg copyOf(Cfg source) {
		Cfg copy = new Cfg();
		copy.configVersion = source.configVersion;
		copy.reloadOptimizerEnabled = source.reloadOptimizerEnabled;
		copy.largeAtlasFixerEnabled = source.largeAtlasFixerEnabled;
		copy.loaderIndexEnabled = source.loaderIndexEnabled;
		copy.loaderZipPoolEnabled = source.loaderZipPoolEnabled;
		copy.loaderTimingsEnabled = source.loaderTimingsEnabled;
		copy.reloadListenerTimingsEnabled = source.reloadListenerTimingsEnabled;
		copy.shaderApplyStallDiagnosticsEnabled = source.shaderApplyStallDiagnosticsEnabled;
		copy.immediatelyFastFontAtlasCompatEnabled = source.immediatelyFastFontAtlasCompatEnabled;
		copy.loadingStatusOverlayEnabled = source.loadingStatusOverlayEnabled;
		copy.loadingScreenFadeOutDisabled = source.loadingScreenFadeOutDisabled;
		copy.reloadSummaryToastEnabled = source.reloadSummaryToastEnabled;
		copy.modelUvTransparencyClampEnabled = source.modelUvTransparencyClampEnabled;
		copy.fontReloadDiagnosticsEnabled = source.fontReloadDiagnosticsEnabled;
		copy.fontPrepareProviderSelectionEnabled = source.fontPrepareProviderSelectionEnabled;
		copy.fontBitmapProviderCacheEnabled = source.fontBitmapProviderCacheEnabled;
		copy.atlasPhaseTimingsEnabled = source.atlasPhaseTimingsEnabled;
		copy.atlasMipParallelEnabled = source.atlasMipParallelEnabled;
		copy.atlasMipBatchSize = source.atlasMipBatchSize;
		copy.atlasDecodeBatchingEnabled = source.atlasDecodeBatchingEnabled;
		copy.atlasDecodeBatchSize = source.atlasDecodeBatchSize;
		copy.modelParseBatchingEnabled = source.modelParseBatchingEnabled;
		copy.modelParseBatchSize = source.modelParseBatchSize;
		copy.modelParseTimingEnabled = source.modelParseTimingEnabled;
		copy.modelAdaptiveBatchingEnabled = source.modelAdaptiveBatchingEnabled;
		copy.modelDuplicateParseCacheEnabled = source.modelDuplicateParseCacheEnabled;
		copy.atlasCapEnabled = source.atlasCapEnabled;
		copy.atlasCapPx = source.atlasCapPx;
		copy.atlasExcludeIds = new ArrayList<>(source.atlasExcludeIds == null ? List.of() : source.atlasExcludeIds);
		copy.atlasRetryEnabled = source.atlasRetryEnabled;
		copy.atlasRetryMaxAttempts = source.atlasRetryMaxAttempts;
		copy.forceDisablePartIIIWithIris = source.forceDisablePartIIIWithIris;
		copy.experimentalAtlasSplit = source.experimentalAtlasSplit;
		copy.atlasSplitTargets = new ArrayList<>(source.atlasSplitTargets == null ? List.of() : source.atlasSplitTargets);
		copy.atlasSplitMaxTiers = source.atlasSplitMaxTiers;
		copy.atlasSplitFallbackToDownscale = source.atlasSplitFallbackToDownscale;
		copy.atlasSplitDisableWithIris = source.atlasSplitDisableWithIris;
		copy.atlasSplitDisableWithSodium = source.atlasSplitDisableWithSodium;
		copy.atlasSplitModelCoherence = source.atlasSplitModelCoherence;
		copy.atlasSplitDiagnostics = source.atlasSplitDiagnostics;
		copy.startupOptimizerEnabled = source.startupOptimizerEnabled;
		copy.startupTimingsEnabled = source.startupTimingsEnabled;
		copy.startupStatusOverlayEnabled = source.startupStatusOverlayEnabled;
		copy.startupExecutorTuningEnabled = source.startupExecutorTuningEnabled;
		copy.startupWorkerThreads = source.startupWorkerThreads;
		copy.startupThreadPriority = source.startupThreadPriority;
		copy.startupSkipWithSmoothBoot = source.startupSkipWithSmoothBoot;
		copy.startupAsyncDataParsingEnabled = source.startupAsyncDataParsingEnabled;
		copy.startupAsyncClassScanEnabled = source.startupAsyncClassScanEnabled;
		copy.startupAsyncFontAtlasEnabled = source.startupAsyncFontAtlasEnabled;
		return copy;
	}

	private static SaveResult write(Cfg cfg) {
		Path file = configFile();
		Path temporaryFile = null;
		try {
			Files.createDirectories(file.getParent());
			temporaryFile = Files.createTempFile(file.getParent(), "packforge-", ".json.tmp");
			try (Writer w = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, w);
			}
			try {
				Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
			}
			return SaveResult.success();
		} catch (IOException e) {
			PackForge.LOGGER.error("Failed to save packforge config", e);
			if (temporaryFile != null) {
				try {
					Files.deleteIfExists(temporaryFile);
				} catch (IOException cleanupException) {
					PackForge.LOGGER.debug("Failed to remove temporary PackForge config {}", temporaryFile, cleanupException);
				}
			}
			return SaveResult.failure(e);
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
		if (!root.has("shaderApplyStallDiagnosticsEnabled")) { cfg.shaderApplyStallDiagnosticsEnabled = defaults.shaderApplyStallDiagnosticsEnabled; changed = true; }
		if (!root.has("immediatelyFastFontAtlasCompatEnabled")) { cfg.immediatelyFastFontAtlasCompatEnabled = defaults.immediatelyFastFontAtlasCompatEnabled; changed = true; }
		if (!root.has("loadingStatusOverlayEnabled")) { cfg.loadingStatusOverlayEnabled = defaults.loadingStatusOverlayEnabled; changed = true; }
		if (!root.has("loadingScreenFadeOutDisabled")) { cfg.loadingScreenFadeOutDisabled = defaults.loadingScreenFadeOutDisabled; changed = true; }
		if (!root.has("reloadSummaryToastEnabled")) { cfg.reloadSummaryToastEnabled = defaults.reloadSummaryToastEnabled; changed = true; }
		if (!root.has("modelUvTransparencyClampEnabled")) { cfg.modelUvTransparencyClampEnabled = defaults.modelUvTransparencyClampEnabled; changed = true; }
		if (!root.has("fontReloadDiagnosticsEnabled")) { cfg.fontReloadDiagnosticsEnabled = defaults.fontReloadDiagnosticsEnabled; changed = true; }
		if (!root.has("fontPrepareProviderSelectionEnabled")) { cfg.fontPrepareProviderSelectionEnabled = defaults.fontPrepareProviderSelectionEnabled; changed = true; }
		if (!root.has("fontBitmapProviderCacheEnabled")) { cfg.fontBitmapProviderCacheEnabled = defaults.fontBitmapProviderCacheEnabled; changed = true; }
		if (!root.has("atlasPhaseTimingsEnabled")) { cfg.atlasPhaseTimingsEnabled = defaults.atlasPhaseTimingsEnabled; changed = true; }
		if (!root.has("atlasMipParallelEnabled")) { cfg.atlasMipParallelEnabled = defaults.atlasMipParallelEnabled; changed = true; }
		if (!root.has("atlasMipBatchSize")) { cfg.atlasMipBatchSize = defaults.atlasMipBatchSize; changed = true; }
		if (!root.has("atlasDecodeBatchingEnabled")) { cfg.atlasDecodeBatchingEnabled = defaults.atlasDecodeBatchingEnabled; changed = true; }
		if (!root.has("atlasDecodeBatchSize")) { cfg.atlasDecodeBatchSize = defaults.atlasDecodeBatchSize; changed = true; }
		if (!root.has("modelParseBatchingEnabled")) { cfg.modelParseBatchingEnabled = defaults.modelParseBatchingEnabled; changed = true; }
		if (!root.has("modelParseBatchSize")) { cfg.modelParseBatchSize = defaults.modelParseBatchSize; changed = true; }
		if (!root.has("modelParseTimingEnabled")) { cfg.modelParseTimingEnabled = defaults.modelParseTimingEnabled; changed = true; }
		if (!root.has("modelAdaptiveBatchingEnabled")) { cfg.modelAdaptiveBatchingEnabled = defaults.modelAdaptiveBatchingEnabled; changed = true; }
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
		if (!root.has("startupOptimizerEnabled")) { cfg.startupOptimizerEnabled = defaults.startupOptimizerEnabled; changed = true; }
		if (!root.has("startupTimingsEnabled")) { cfg.startupTimingsEnabled = defaults.startupTimingsEnabled; changed = true; }
		if (!root.has("startupStatusOverlayEnabled")) { cfg.startupStatusOverlayEnabled = defaults.startupStatusOverlayEnabled; changed = true; }
		if (!root.has("startupExecutorTuningEnabled")) { cfg.startupExecutorTuningEnabled = defaults.startupExecutorTuningEnabled; changed = true; }
		if (!root.has("startupWorkerThreads")) { cfg.startupWorkerThreads = defaults.startupWorkerThreads; changed = true; }
		if (!root.has("startupThreadPriority")) { cfg.startupThreadPriority = defaults.startupThreadPriority; changed = true; }
		if (!root.has("startupSkipWithSmoothBoot")) { cfg.startupSkipWithSmoothBoot = defaults.startupSkipWithSmoothBoot; changed = true; }
		if (!root.has("startupAsyncDataParsingEnabled")) { cfg.startupAsyncDataParsingEnabled = defaults.startupAsyncDataParsingEnabled; changed = true; }
		if (!root.has("startupAsyncClassScanEnabled")) { cfg.startupAsyncClassScanEnabled = defaults.startupAsyncClassScanEnabled; changed = true; }
		if (!root.has("startupAsyncFontAtlasEnabled")) { cfg.startupAsyncFontAtlasEnabled = defaults.startupAsyncFontAtlasEnabled; changed = true; }

		cfg.atlasCapPx = clamp(cfg.atlasCapPx, 16, 8192);
		cfg.atlasRetryMaxAttempts = clamp(cfg.atlasRetryMaxAttempts, 1, 10);
		cfg.atlasMipBatchSize = clamp(cfg.atlasMipBatchSize, 16, 4096);
		cfg.atlasDecodeBatchSize = clamp(cfg.atlasDecodeBatchSize, 16, 4096);
		cfg.modelParseBatchSize = clamp(cfg.modelParseBatchSize, 8, 1024);
		cfg.atlasSplitMaxTiers = clamp(cfg.atlasSplitMaxTiers, 1, 4);
		cfg.startupWorkerThreads = clamp(cfg.startupWorkerThreads, 0, Runtime.getRuntime().availableProcessors());
		cfg.startupThreadPriority = clamp(cfg.startupThreadPriority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
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
		int oldThreads = cfg.startupWorkerThreads;
		int oldPriority = cfg.startupThreadPriority;
		int oldAtlasDecodeBatch = cfg.atlasDecodeBatchSize;
		cfg.startupWorkerThreads = clamp(cfg.startupWorkerThreads, 0, Runtime.getRuntime().availableProcessors());
		cfg.startupThreadPriority = clamp(cfg.startupThreadPriority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
		cfg.atlasDecodeBatchSize = clamp(cfg.atlasDecodeBatchSize, 16, 4096);
		return changed || oldTiers != cfg.atlasSplitMaxTiers || oldThreads != cfg.startupWorkerThreads || oldPriority != cfg.startupThreadPriority || oldAtlasDecodeBatch != cfg.atlasDecodeBatchSize;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private PackForgeConfig() {}
}
