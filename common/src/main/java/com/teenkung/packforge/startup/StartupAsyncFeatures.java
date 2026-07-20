package com.teenkung.packforge.startup;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.platform.PackForgeServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class StartupAsyncFeatures {
	public static void startConfiguredWork() {
		if (!FeatureFlags.startupOptimizerEnabled()) {
			return;
		}
		if (FeatureFlags.startupAsyncDataParsingEnabled()) {
			PackForge.LOGGER.info("PackForge startup: async data parsing active; reload-side model JSON batching, adaptive batches, and duplicate parse cache are enabled");
		}
		if (FeatureFlags.startupAsyncFontAtlasEnabled()) {
			PackForge.LOGGER.info("PackForge startup: async font/atlas startup active; CPU-only font selection, atlas decode batching, and mip prep are enabled");
		}
		if (FeatureFlags.startupAsyncClassScanEnabled()) {
			startClassScanTelemetry();
		}
	}

	private static void startClassScanTelemetry() {
		Executor executor = PackForgeServices.platform().backgroundExecutor();
		CompletableFuture.runAsync(StartupAsyncFeatures::scanModJars, executor);
	}

	private static void scanModJars() {
		long startNs = System.nanoTime();
		Path mods = PackForgeServices.platform().gameDirectory().resolve("mods");
		if (!Files.isDirectory(mods)) {
			PackForge.LOGGER.info("PackForge startup class scan: skipped; mods directory not found");
			return;
		}
		int jars = 0;
		int classes = 0;
		long bytes = 0L;
		try (Stream<Path> paths = Files.list(mods)) {
			for (Path path : paths.filter(StartupAsyncFeatures::isJar).toList()) {
				jars++;
				try (ZipFile zip = new ZipFile(path.toFile())) {
					bytes += Files.size(path);
					for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
						if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
							classes++;
						}
					}
				} catch (IOException e) {
					PackForge.LOGGER.debug("PackForge startup class scan: skipped unreadable jar {}", path.getFileName(), e);
				}
			}
		} catch (IOException e) {
			PackForge.LOGGER.warn("PackForge startup class scan failed", e);
			return;
		}
		long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
		PackForge.LOGGER.info("PackForge startup class scan: jars={} classes={} bytes={} elapsed={}ms mode=observed-only",
			jars, classes, bytes, elapsedMs);
		StartupTimings.recordDuration("async_class_scan_telemetry", System.nanoTime() - startNs);
	}

	private static boolean isJar(Path path) {
		String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		return name.endsWith(".jar");
	}

	private StartupAsyncFeatures() {}
}
