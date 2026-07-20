package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class LoaderTimings {
	private static final AtomicLong getResourceCalls = new AtomicLong();
	private static final AtomicLong getNamespacesCalls = new AtomicLong();
	private static final AtomicLong listResourcesCalls = new AtomicLong();
	private static final AtomicLong fullScansAvoided = new AtomicLong();
	private static final ConcurrentHashMap<String, ListenerTiming> listenerTimings = new ConcurrentHashMap<>();
	private static volatile long reloadStartNs;
	private static volatile long reloadSessionId;

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
		reloadSessionId = ReloadSessionTracker.current().id();
		reloadStartNs = System.nanoTime();
		if (!FeatureFlags.loaderTimingsEnabled() && !FeatureFlags.reloadListenerTimingsEnabled()) return;
		getResourceCalls.set(0);
		getNamespacesCalls.set(0);
		listResourcesCalls.set(0);
		fullScansAvoided.set(0);
		listenerTimings.clear();
	}

	public static void onReloadEnd(Throwable error) {
		if (!FeatureFlags.loaderTimingsEnabled()) return;
		long elapsedMs = (System.nanoTime() - reloadStartNs) / 1_000_000L;
		long gr = getResourceCalls.get();
		long gn = getNamespacesCalls.get();
		long lr = listResourcesCalls.get();
		long avoided = fullScansAvoided.get();
		PackForge.LOGGER.info("PackForge reload complete: id={} elapsed={}ms status={} getResource={} getNamespaces={} listResources={} fullScansAvoided={}",
			reloadSessionId, elapsedMs, error == null ? "ok" : "failed", gr, gn, lr, avoided);
		writeReloadCountersCsvAsync(reloadSessionId, elapsedMs, gr, gn, lr, avoided);
	}

	private static void writeReloadCountersCsvAsync(long sessionId, long elapsedMs, long gr, long gn, long lr, long avoided) {
		CompletableFuture.runAsync(() -> writeReloadCountersCsv(sessionId, elapsedMs, gr, gn, lr, avoided), backgroundExecutor());
	}

	private static void writeReloadCountersCsv(long sessionId, long elapsedMs, long gr, long gn, long lr, long avoided) {
		try {
			Path csv = Path.of("logs", "packforge-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) w.write("timestamp,reload_id,elapsed_ms,getResource,getNamespaces,listResources,fullScansAvoided\n");
				w.write(System.currentTimeMillis() + "," + sessionId + "," + elapsedMs + "," + gr + "," + gn + "," + lr + "," + avoided + "\n");
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

		ReloadSessionTracker.ReloadSession session = ReloadSessionTracker.current();
		PackForge.LOGGER.info("PackForge Loading Summary: id={} elapsed={}ms status={} source={} added={} removed={}",
			session.id(), elapsedMs, error == null ? "ok" : "failed", session.source(), session.added(), session.removed());
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
		logApplyStalls(session.id(), applyReports);
		writeListenerCsvAsync(session.id(), elapsedMs, reports);
	}

	private static void logApplyStalls(long sessionId, List<ListenerReport> applyReports) {
		if (!FeatureFlags.shaderApplyStallDiagnosticsEnabled()) {
			return;
		}
		for (ListenerReport report : applyReports) {
			long stallMs = report.applySortMs();
			if (stallMs < 1000L) {
				continue;
			}
			if ("Shader Loader".equals(report.name)) {
				PackForge.LOGGER.info("PackForge reload stall: id={} listener={} apply={}ms max={}ms reason=shader pipeline apply on render thread; observed only",
					sessionId, report.name, report.applyMs, report.applyMaxMs);
			} else {
				PackForge.LOGGER.info("PackForge reload stall: id={} listener={} apply={}ms max={}ms reason=render-thread apply work",
					sessionId, report.name, report.applyMs, report.applyMaxMs);
			}
		}
	}

	private static ListenerTiming timing(String listenerName) {
		String name = listenerName == null || listenerName.isBlank() ? "unknown" : listenerName;
		return listenerTimings.computeIfAbsent(name, ignored -> new ListenerTiming());
	}

	private static void writeListenerCsvAsync(long sessionId, long elapsedMs, List<ListenerReport> reports) {
		CompletableFuture.runAsync(() -> writeListenerCsv(sessionId, elapsedMs, reports), backgroundExecutor());
	}

	private static void writeListenerCsv(long sessionId, long elapsedMs, List<ListenerReport> reports) {
		try {
			Path csv = Path.of("logs", "packforge-listener-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) {
					w.write("timestamp,reload_id,reload_elapsed_ms,listener,wall_ms,prepare_ms,prepare_tasks,prepare_max_ms,apply_ms,apply_tasks,apply_max_ms\n");
				}
				long now = System.currentTimeMillis();
				for (ListenerReport report : reports) {
					w.write(now + "," + sessionId + "," + elapsedMs + "," + csv(report.name) + "," + report.wallMs + "," + report.prepareMs + "," +
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

	private static Executor backgroundExecutor() {
		return PackForgeServices.isInitialized() ? PackForgeServices.platform().backgroundExecutor() : ForkJoinPool.commonPool();
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
