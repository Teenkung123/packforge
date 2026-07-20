package com.teenkung.packforge.startup;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.platform.PackForgeServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.LongAdder;

public final class StartupTimings {
	private static volatile long startNs = System.nanoTime();
	private static volatile long lastMarkNs = startNs;
	private static final ConcurrentHashMap<String, LongAdder> phaseDurations = new ConcurrentHashMap<>();
	private static volatile boolean recorded;
	private static volatile boolean complete;

	public static void reset() {
		startNs = System.nanoTime();
		lastMarkNs = startNs;
		phaseDurations.clear();
		recorded = false;
		complete = false;
	}

	public static void resetIfUnused() {
		if (!recorded && phaseDurations.isEmpty()) {
			reset();
		}
	}

	public static boolean hasRecordedWork() {
		return recorded || !phaseDurations.isEmpty();
	}

	public static boolean isActive() {
		return startupTimingsEnabled() && !complete;
	}

	public static void complete() {
		if (complete) {
			return;
		}
		complete = true;
		logSummary();
	}

	public static void mark(String phase) {
		if (!isActive()) {
			return;
		}
		long now = System.nanoTime();
		long elapsedMs = (now - startNs) / 1_000_000L;
		long deltaMs = (now - lastMarkNs) / 1_000_000L;
		lastMarkNs = now;
		addDuration(phase, deltaMs);
		PackForge.LOGGER.info("PackForge startup: phase={} elapsed={}ms delta={}ms", phase, elapsedMs, deltaMs);
		writeCsv(phase, elapsedMs, deltaMs);
	}

	public static void markBoundary(String boundaryName, String previousBucket) {
		if (!isActive()) {
			return;
		}
		long now = System.nanoTime();
		long elapsedMs = (now - startNs) / 1_000_000L;
		long deltaMs = (now - lastMarkNs) / 1_000_000L;
		lastMarkNs = now;
		addDuration(previousBucket, deltaMs);
		PackForge.LOGGER.info("PackForge startup: boundary={} previousBucket={} elapsed={}ms delta={}ms",
			boundaryName, previousBucket, elapsedMs, deltaMs);
		writeCsv(boundaryName, elapsedMs, deltaMs);
	}

	public static void event(String eventName) {
		if (!isActive()) {
			return;
		}
		long now = System.nanoTime();
		long elapsedMs = (now - startNs) / 1_000_000L;
		long deltaMs = (now - lastMarkNs) / 1_000_000L;
		lastMarkNs = now;
		PackForge.LOGGER.info("PackForge startup: event={} elapsed={}ms delta={}ms", eventName, elapsedMs, deltaMs);
		writeCsv(eventName, elapsedMs, deltaMs);
	}

	public static void recordDuration(String phase, long elapsedNs) {
		if (!isActive()) {
			return;
		}
		addDuration(phase, elapsedNs / 1_000_000L);
	}

	public static void recordStall(String source, long elapsedNs) {
		if (!isActive()) {
			return;
		}
		long elapsedMs = elapsedNs / 1_000_000L;
		if (elapsedMs < 50L) {
			return;
		}
		addDuration("first_frame_stalls", elapsedMs);
		PackForge.LOGGER.info("PackForge startup stall: source={} elapsed={}ms", source, elapsedMs);
		writeCsv("stall_" + source, (System.nanoTime() - startNs) / 1_000_000L, elapsedMs);
	}

	public static void executorTuning(String executorName, int vanillaThreads, int configuredThreads, int priority, String status) {
		if (!isActive()) {
			return;
		}
		long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
		recorded = true;
		PackForge.LOGGER.info("PackForge startup executor: name={} status={} vanillaThreads={} configuredThreads={} priority={} elapsed={}ms",
			executorName, status, vanillaThreads, configuredThreads, priority, elapsedMs);
		writeCsv("executor_" + status + "_" + executorName, elapsedMs, 0L);
	}

	public static void logSummary() {
		if (!startupTimingsEnabled() || (!isActive() && !hasRecordedWork())) {
			return;
		}
		long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
		List<PhaseReport> reports = new ArrayList<>();
		phaseDurations.forEach((name, timing) -> reports.add(new PhaseReport(name, timing.sum())));
		reports.sort(Comparator.comparingLong(PhaseReport::elapsedMs).reversed());
		PackForge.LOGGER.info("PackForge startup summary: elapsed={}ms trackedPhases={}", elapsedMs, reports.size());
		for (PhaseReport report : reports.stream().filter(PhaseReport::hasTime).limit(10).toList()) {
			PackForge.LOGGER.info("  {} - {}ms", report.name(), report.elapsedMs());
		}
		writeSummaryCsvAsync(elapsedMs, reports);
	}

	private static boolean startupTimingsEnabled() {
		if (PackForgeConfig.isLoaded()) {
			return FeatureFlags.startupTimingsEnabled();
		}
		StartupEarlyConfig.Settings settings = StartupEarlyConfig.get();
		return settings.startupOptimizerEnabled() && settings.startupTimingsEnabled();
	}

	private static void addDuration(String phase, long elapsedMs) {
		if (elapsedMs <= 0L) {
			return;
		}
		recorded = true;
		phaseDurations.computeIfAbsent(phase, ignored -> new LongAdder()).add(elapsedMs);
	}

	private static void writeCsv(String phase, long elapsedMs, long deltaMs) {
		writeCsvNow(phase, elapsedMs, deltaMs);
	}

	private static void writeCsvNow(String phase, long elapsedMs, long deltaMs) {
		try {
			Path csv = Path.of("logs", "packforge-startup-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) {
					w.write("timestamp,phase,elapsed_ms,delta_ms\n");
				}
				w.write(System.currentTimeMillis() + "," + csv(phase) + "," + elapsedMs + "," + deltaMs + "\n");
			}
		} catch (IOException e) {
			PackForge.LOGGER.warn("Failed to write startup timings CSV", e);
		}
	}

	private static void writeSummaryCsvAsync(long elapsedMs, List<PhaseReport> reports) {
		CompletableFuture.runAsync(() -> writeSummaryCsvNow(elapsedMs, reports), backgroundExecutor());
	}

	private static void writeSummaryCsvNow(long elapsedMs, List<PhaseReport> reports) {
		try {
			Path csv = Path.of("logs", "packforge-startup-summary.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) {
					w.write("timestamp,startup_elapsed_ms,phase,phase_ms\n");
				}
				long now = System.currentTimeMillis();
				for (PhaseReport report : reports) {
					w.write(now + "," + elapsedMs + "," + csv(report.name()) + "," + report.elapsedMs() + "\n");
				}
			}
		} catch (IOException e) {
			PackForge.LOGGER.warn("Failed to write startup timings CSV", e);
		}
	}

	private static Executor backgroundExecutor() {
		return PackForgeServices.isInitialized() ? PackForgeServices.platform().backgroundExecutor() : ForkJoinPool.commonPool();
	}

	private static String csv(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private StartupTimings() {}

	private record PhaseReport(String name, long elapsedMs) {
		boolean hasTime() {
			return elapsedMs > 0L;
		}
	}
}
