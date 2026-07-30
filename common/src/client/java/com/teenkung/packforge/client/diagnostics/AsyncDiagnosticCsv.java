package com.teenkung.packforge.client.diagnostics;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgeServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Asynchronous append-only CSV writes with immutable caller snapshots. */
public final class AsyncDiagnosticCsv {
	public static CompletableFuture<Void> append(Path file, String header, List<String> rows) {
		List<String> snapshot = List.copyOf(rows);
		return CompletableFuture.runAsync(() -> write(file, header, snapshot), executor());
	}

	static void write(Path file, String header, List<String> rows) {
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			boolean exists = Files.exists(file);
			try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				if (!exists && header != null && !header.isBlank()) writer.write(header + "\n");
				for (String row : rows) writer.write(row + "\n");
			}
		} catch (IOException exception) {
			PackForge.LOGGER.warn("Failed to write PackForge diagnostic CSV {}", file, exception);
		}
	}

	private static Executor executor() {
		return PackForgeServices.isInitialized() ? PackForgeServices.platform().backgroundExecutor() : ForkJoinPool.commonPool();
	}

	private AsyncDiagnosticCsv() {}
}
