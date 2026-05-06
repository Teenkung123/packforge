package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class LoaderTimings {
	private static final AtomicLong getResourceCalls = new AtomicLong();
	private static final AtomicLong getNamespacesCalls = new AtomicLong();
	private static final AtomicLong listResourcesCalls = new AtomicLong();
	private static final AtomicLong fullScansAvoided = new AtomicLong();
	private static volatile long reloadStartNs;

	public static void recordGetResource() {
		if (!FeatureFlags.loaderTimingsEnabled()) return;
		getResourceCalls.incrementAndGet();
	}

	public static void recordGetNamespaces() {
		if (!FeatureFlags.loaderTimingsEnabled()) return;
		getNamespacesCalls.incrementAndGet();
		fullScansAvoided.incrementAndGet();
	}

	public static void recordListResources() {
		if (!FeatureFlags.loaderTimingsEnabled()) return;
		listResourcesCalls.incrementAndGet();
		fullScansAvoided.incrementAndGet();
	}

	public static void onReloadStart() {
		if (!FeatureFlags.loaderTimingsEnabled()) return;
		reloadStartNs = System.nanoTime();
		getResourceCalls.set(0);
		getNamespacesCalls.set(0);
		listResourcesCalls.set(0);
		fullScansAvoided.set(0);
	}

	public static void onReloadEnd() {
		if (!FeatureFlags.loaderTimingsEnabled()) return;
		long elapsedMs = (System.nanoTime() - reloadStartNs) / 1_000_000L;
		long gr = getResourceCalls.get();
		long gn = getNamespacesCalls.get();
		long lr = listResourcesCalls.get();
		long avoided = fullScansAvoided.get();
		PackForge.LOGGER.info("PackForge reload: elapsed={}ms getResource={} getNamespaces={} listResources={} fullScansAvoided={}",
			elapsedMs, gr, gn, lr, avoided);
		try {
			Path csv = Path.of("logs", "packforge-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) w.write("timestamp,elapsed_ms,getResource,getNamespaces,listResources,fullScansAvoided\n");
				w.write(System.currentTimeMillis() + "," + elapsedMs + "," + gr + "," + gn + "," + lr + "," + avoided + "\n");
			}
		} catch (IOException e) {
			PackForge.LOGGER.warn("Failed to write timings CSV", e);
		}
	}

	private LoaderTimings() {}
}
