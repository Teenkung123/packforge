package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgeServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Reload counters and listener timings scoped to one immutable feature snapshot. */
public final class LoaderTimings {
	public static void recordGetResource() {
		recordGetResource(ReloadExecutionContext.current());
	}

	public static void recordGetResource(ReloadExecutionContext context) {
		if (loaderTimingsEnabled(context)) {
			context.metrics().recordGetResource();
		}
	}

	public static void recordGetNamespaces() {
		recordGetNamespaces(ReloadExecutionContext.current());
	}

	public static void recordGetNamespaces(ReloadExecutionContext context) {
		if (loaderTimingsEnabled(context)) {
			context.metrics().recordGetNamespaces();
		}
	}

	public static void recordListResources() {
		recordListResources(ReloadExecutionContext.current());
	}

	public static void recordListResources(ReloadExecutionContext context) {
		if (loaderTimingsEnabled(context)) {
			context.metrics().recordListResources();
		}
	}

	public static void onReloadStart() {
		onReloadStart(ReloadExecutionContext.current());
	}

	public static void onReloadStart(ReloadExecutionContext context) {
		if (context != null) {
			ReloadHooks.fireStart();
		}
	}

	public static void onReloadEnd(Throwable error) {
		onReloadEnd(ReloadExecutionContext.current(), error);
	}

	public static void onReloadEnd(ReloadExecutionContext context, Throwable error) {
		if (!current(context) || !loaderTimingsEnabled(context)) {
			return;
		}
		long elapsedMs = context.metrics().elapsedNs() / 1_000_000L;
		ReloadMetrics.CounterSnapshot counters = context.metrics().counters();
		PackForge.LOGGER.info("PackForge reload complete: id={} elapsed={}ms status={} getResource={} getNamespaces={} listResources={} fullScansAvoided={}",
			context.reloadId(), elapsedMs, error == null ? "ok" : "failed",
			counters.getResourceCalls(), counters.getNamespacesCalls(),
			counters.listResourcesCalls(), counters.fullScansAvoided());
		writeReloadCountersCsvAsync(context.reloadId(), elapsedMs, counters);
	}

	public static void recordListenerWall(String listenerName, long elapsedNs) {
		recordListenerWall(ReloadExecutionContext.current(), listenerName, elapsedNs);
	}

	public static void recordListenerWall(ReloadExecutionContext context, String listenerName, long elapsedNs) {
		if (listenerTimingsEnabled(context)) {
			context.metrics().recordListenerWall(listenerName, elapsedNs);
		}
	}

	public static void recordListenerPrepare(String listenerName, long elapsedNs) {
		recordListenerPrepare(ReloadExecutionContext.current(), listenerName, elapsedNs);
	}

	public static void recordListenerPrepare(ReloadExecutionContext context, String listenerName, long elapsedNs) {
		if (listenerTimingsEnabled(context)) {
			context.metrics().recordListenerPrepare(listenerName, elapsedNs);
		}
	}

	public static void recordListenerApply(String listenerName, long elapsedNs) {
		recordListenerApply(ReloadExecutionContext.current(), listenerName, elapsedNs);
	}

	public static void recordListenerApply(ReloadExecutionContext context, String listenerName, long elapsedNs) {
		if (listenerTimingsEnabled(context)) {
			context.metrics().recordListenerApply(listenerName, elapsedNs);
		}
	}

	public static void onReloadComplete(Throwable error) {
		onReloadComplete(ReloadExecutionContext.current(), error);
	}

	public static void onReloadComplete(ReloadExecutionContext context, Throwable error) {
		if (!current(context) || !listenerTimingsEnabled(context)) {
			return;
		}
		long elapsedMs = context.metrics().elapsedNs() / 1_000_000L;
		List<ListenerReport> reports = context.metrics().listenerSnapshots().stream()
			.map(ListenerReport::from)
			.sorted(Comparator.comparingLong(ListenerReport::activeMs).reversed())
			.toList();
		if (reports.isEmpty()) {
			return;
		}

		PackForge.LOGGER.info("PackForge Loading Summary: id={} elapsed={}ms status={}",
			context.reloadId(), elapsedMs, error == null ? "ok" : "failed");
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
		logApplyStalls(context, applyReports);
		writeListenerCsvAsync(context.reloadId(), elapsedMs, reports);
	}

	private static void logApplyStalls(ReloadExecutionContext context, List<ListenerReport> applyReports) {
		if (!context.features().shaderApplyStallDiagnosticsEnabled()) {
			return;
		}
		for (ListenerReport report : applyReports) {
			long stallMs = report.applySortMs();
			if (stallMs < 1000L) {
				continue;
			}
			if ("Shader Loader".equals(report.name)) {
				PackForge.LOGGER.info("PackForge reload stall: id={} listener={} apply={}ms max={}ms reason=shader pipeline apply on render thread; observed only",
					context.reloadId(), report.name, report.applyMs, report.applyMaxMs);
			} else {
				PackForge.LOGGER.info("PackForge reload stall: id={} listener={} apply={}ms max={}ms reason=render-thread apply work",
					context.reloadId(), report.name, report.applyMs, report.applyMaxMs);
			}
		}
	}

	private static void writeReloadCountersCsvAsync(long sessionId, long elapsedMs, ReloadMetrics.CounterSnapshot counters) {
		CompletableFuture.runAsync(
			() -> writeReloadCountersCsv(sessionId, elapsedMs, counters),
			backgroundExecutor()
		);
	}

	private static void writeReloadCountersCsv(long sessionId, long elapsedMs, ReloadMetrics.CounterSnapshot counters) {
		try {
			Path csv = Path.of("logs", "packforge-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var writer = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) writer.write("timestamp,reload_id,elapsed_ms,getResource,getNamespaces,listResources,fullScansAvoided\n");
				writer.write(System.currentTimeMillis() + "," + sessionId + "," + elapsedMs + "," +
					counters.getResourceCalls() + "," + counters.getNamespacesCalls() + "," +
					counters.listResourcesCalls() + "," + counters.fullScansAvoided() + "\n");
			}
		} catch (IOException exception) {
			PackForge.LOGGER.warn("Failed to write timings CSV", exception);
		}
	}

	private static void writeListenerCsvAsync(long sessionId, long elapsedMs, List<ListenerReport> reports) {
		CompletableFuture.runAsync(() -> writeListenerCsv(sessionId, elapsedMs, reports), backgroundExecutor());
	}

	private static void writeListenerCsv(long sessionId, long elapsedMs, List<ListenerReport> reports) {
		try {
			Path csv = Path.of("logs", "packforge-listener-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var writer = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) {
					writer.write("timestamp,reload_id,reload_elapsed_ms,listener,wall_ms,prepare_ms,prepare_tasks,prepare_max_ms,apply_ms,apply_tasks,apply_max_ms\n");
				}
				long now = System.currentTimeMillis();
				for (ListenerReport report : reports) {
					writer.write(now + "," + sessionId + "," + elapsedMs + "," + csv(report.name) + "," + report.wallMs + "," + report.prepareMs + "," +
						report.prepareTasks + "," + report.prepareMaxMs + "," + report.applyMs + "," + report.applyTasks + "," + report.applyMaxMs + "\n");
				}
			}
		} catch (IOException exception) {
			PackForge.LOGGER.warn("Failed to write listener timings CSV", exception);
		}
	}

	private static String csv(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static boolean current(ReloadExecutionContext context) {
		return ReloadExecutionContext.isCurrent(context);
	}

	private static boolean loaderTimingsEnabled(ReloadExecutionContext context) {
		return context != null && context.features().loaderTimingsEnabled();
	}

	private static boolean listenerTimingsEnabled(ReloadExecutionContext context) {
		return context != null && context.features().reloadListenerTimingsEnabled();
	}

	private static Executor backgroundExecutor() {
		return PackForgeServices.isInitialized() ? PackForgeServices.platform().backgroundExecutor() : ForkJoinPool.commonPool();
	}

	private LoaderTimings() {}

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
		static ListenerReport from(ReloadMetrics.ListenerSnapshot snapshot) {
			return new ListenerReport(
				snapshot.name(),
				ms(snapshot.wallNs()),
				ms(snapshot.prepareNs()),
				snapshot.prepareTasks(),
				ms(snapshot.prepareMaxNs()),
				ms(snapshot.applyNs()),
				snapshot.applyTasks(),
				ms(snapshot.applyMaxNs())
			);
		}

		long activeMs() {
			return prepareMs + applyMs;
		}

		long applySortMs() {
			return Math.max(applyMs, applyMaxMs);
		}

		boolean hasActiveWork() {
			return prepareTasks > 0L || applyTasks > 0L || activeMs() > 0L;
		}
	}

	private static long ms(long ns) {
		return Math.max(0L, ns / 1_000_000L);
	}
}
