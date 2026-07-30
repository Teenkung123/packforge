package com.teenkung.packforge.client.diagnostics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** String-keyed timing collector usable by version-specific atlas and reload phases. */
public final class ReloadPhaseTimings {
	private final ConcurrentHashMap<String, LongAdder> timings = new ConcurrentHashMap<>();

	public long start() { return System.nanoTime(); }

	public void record(String phase, long startNs) {
		if (startNs == 0L) return;
		timings.computeIfAbsent(phase, ignored -> new LongAdder()).add(System.nanoTime() - startNs);
	}

	public List<Phase> snapshot() {
		List<Phase> result = new ArrayList<>();
		timings.forEach((phase, elapsed) -> result.add(new Phase(phase, elapsed.sum())));
		result.sort(Comparator.comparing(Phase::name));
		return List.copyOf(result);
	}

	public void writeCsvAsync(Path file) {
		long now = System.currentTimeMillis();
		List<String> rows = snapshot().stream().map(phase -> now + "," + csv(phase.name()) + "," + phase.elapsedNs() / 1_000_000L).toList();
		AsyncDiagnosticCsv.append(file, "timestamp,phase,elapsed_ms", rows);
	}

	public void reset() { timings.clear(); }

	private static String csv(String value) {
		return value.indexOf(',') < 0 && value.indexOf('"') < 0 ? value : '"' + value.replace("\"", "\"\"") + '"';
	}

	public record Phase(String name, long elapsedNs) {}
}
