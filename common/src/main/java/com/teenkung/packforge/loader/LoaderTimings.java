package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class LoaderTimings {
	private static final AtomicLong getResourceCalls = new AtomicLong();
	private static final AtomicLong getNamespacesCalls = new AtomicLong();
	private static final AtomicLong listResourcesCalls = new AtomicLong();
	private static final AtomicLong fullScansAvoided = new AtomicLong();
	private static final ConcurrentHashMap<String, ListenerTiming> listenerTimings = new ConcurrentHashMap<>();
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
		ReloadHooks.fireStart();
		if (!FeatureFlags.loaderTimingsEnabled() && !FeatureFlags.reloadListenerTimingsEnabled()) return;
		reloadStartNs = System.nanoTime();
		getResourceCalls.set(0);
		getNamespacesCalls.set(0);
		listResourcesCalls.set(0);
		fullScansAvoided.set(0);
		listenerTimings.clear();
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

	public static void recordListenerWall(String listenerName, long elapsedNs) {
		if (!FeatureFlags.reloadListenerTimingsEnabled()) return;
		timing(listenerName).wallNs.add(elapsedNs);
	}

	public static void recordListenerPrepare(String listenerName, long elapsedNs) {
		if (!FeatureFlags.reloadListenerTimingsEnabled()) return;
		ListenerTiming timing = timing(listenerName);
		timing.prepareNs.add(elapsedNs);
		timing.prepareTasks.increment();
		timing.prepareMaxNs.accumulateAndGet(elapsedNs, Math::max);
	}

	public static void recordListenerApply(String listenerName, long elapsedNs) {
		if (!FeatureFlags.reloadListenerTimingsEnabled()) return;
		ListenerTiming timing = timing(listenerName);
		timing.applyNs.add(elapsedNs);
		timing.applyTasks.increment();
		timing.applyMaxNs.accumulateAndGet(elapsedNs, Math::max);
	}

	public static void onReloadComplete(Throwable error) {
		if (!FeatureFlags.reloadListenerTimingsEnabled()) return;
		long elapsedMs = (System.nanoTime() - reloadStartNs) / 1_000_000L;
		List<ListenerReport> reports = listenerTimings.entrySet().stream()
			.map(entry -> ListenerReport.from(entry.getKey(), entry.getValue()))
			.sorted(Comparator.comparingLong(ListenerReport::activeMs).reversed())
			.toList();
		if (reports.isEmpty()) {
			return;
		}

		PackForge.LOGGER.info("PackForge Loading Summary: elapsed={}ms status={}", elapsedMs, error == null ? "ok" : "failed");
		PackForge.LOGGER.info("PackForge Loading Summary: slowest active work");
		for (ListenerReport report : reports.stream().filter(ListenerReport::hasActiveWork).limit(12).toList()) {
			PackForge.LOGGER.info("  {} - {}ms active (prepare {}ms/{} tasks, max {}ms; apply {}ms/{} tasks, max {}ms; wall {}ms)",
				report.name, report.activeMs(), report.prepareMs, report.prepareTasks, report.prepareMaxMs,
				report.applyMs, report.applyTasks, report.applyMaxMs, report.wallMs);
		}

		List<ListenerReport> applyReports = reports.stream()
			.filter(report -> report.applyMs > 0L || report.applyMaxMs > 0L)
			.sorted(Comparator.comparingLong(ListenerReport::applySortMs).reversed())
			.limit(8)
			.toList();
		if (!applyReports.isEmpty()) {
			PackForge.LOGGER.info("PackForge Loading Summary: render-thread apply stalls");
			for (ListenerReport report : applyReports) {
				PackForge.LOGGER.info("  {} - apply {}ms/{} tasks, max {}ms",
					report.name, report.applyMs, report.applyTasks, report.applyMaxMs);
			}
		}
		writeListenerCsv(elapsedMs, reports);
	}

	private static ListenerTiming timing(String listenerName) {
		String name = listenerName == null || listenerName.isBlank() ? "unknown" : listenerName;
		return listenerTimings.computeIfAbsent(name, ignored -> new ListenerTiming());
	}

	private static void writeListenerCsv(long elapsedMs, List<ListenerReport> reports) {
		try {
			Path csv = Path.of("logs", "packforge-listener-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) {
					w.write("timestamp,reload_elapsed_ms,listener,wall_ms,prepare_ms,prepare_tasks,prepare_max_ms,apply_ms,apply_tasks,apply_max_ms\n");
				}
				long now = System.currentTimeMillis();
				for (ListenerReport report : reports) {
					w.write(now + "," + elapsedMs + "," + csv(report.name) + "," + report.wallMs + "," + report.prepareMs + "," +
						report.prepareTasks + "," + report.prepareMaxMs + "," + report.applyMs + "," + report.applyTasks + "," + report.applyMaxMs + "\n");
				}
			}
		} catch (IOException e) {
			PackForge.LOGGER.warn("Failed to write listener timings CSV", e);
		}
	}

	private static String csv(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	private LoaderTimings() {}

	private static final class ListenerTiming {
		final LongAdder wallNs = new LongAdder();
		final LongAdder prepareNs = new LongAdder();
		final LongAdder prepareTasks = new LongAdder();
		final AtomicLong prepareMaxNs = new AtomicLong();
		final LongAdder applyNs = new LongAdder();
		final LongAdder applyTasks = new LongAdder();
		final AtomicLong applyMaxNs = new AtomicLong();
	}

	private record ListenerReport(
		String name,
		long wallMs,
		long prepareMs,
		long prepareTasks,
		long prepareMaxMs,
		long applyMs,
		long applyTasks,
		long applyMaxMs
	) {
		static ListenerReport from(String name, ListenerTiming timing) {
			return new ListenerReport(
				name,
				ms(timing.wallNs.sum()),
				ms(timing.prepareNs.sum()),
				timing.prepareTasks.sum(),
				ms(timing.prepareMaxNs.get()),
				ms(timing.applyNs.sum()),
				timing.applyTasks.sum(),
				ms(timing.applyMaxNs.get())
			);
		}

		long activeMs() {
			return this.prepareMs + this.applyMs;
		}

		long applySortMs() {
			return Math.max(this.applyMs, this.applyMaxMs);
		}

		boolean hasActiveWork() {
			return this.prepareTasks > 0L || this.applyTasks > 0L || this.activeMs() > 0L;
		}
	}
}
