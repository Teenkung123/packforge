package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipReadPoolTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void boundsConcurrentHandlesAndReleasesWindowsFileLock() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("pool.zip"), 100);
		ZipReadPool pool = new ZipReadPool(archive.toFile(), 2);
		ExecutorService executor = Executors.newFixedThreadPool(16);
		try {
			List<Callable<String>> reads = new ArrayList<>();
			for (int i = 0; i < 64; i++) {
				reads.add(() -> {
					try (InputStream input = pool.open(
						"assets/minecraft/textures/a.txt",
						() -> new ByteArrayInputStream("fallback".getBytes(StandardCharsets.UTF_8)),
						failure -> {}
					)) {
						return new String(input.readAllBytes(), StandardCharsets.UTF_8);
					}
				});
			}
			for (Future<String> result : executor.invokeAll(reads)) {
				assertEquals("alpha", result.get());
			}
			assertTrue(pool.openHandleCount() <= 2);
		} finally {
			executor.shutdownNow();
			pool.close();
		}
		assertEquals(0, pool.openHandleCount());

		Path renamed = temporaryDirectory.resolve("pool-renamed.zip");
		Files.move(archive, renamed);
		Files.delete(renamed);
	}

	@Test
	void failedPoolReportsOnceAndAlwaysFallsBack() throws IOException {
		Path missing = temporaryDirectory.resolve("missing.zip");
		ZipReadPool pool = new ZipReadPool(missing.toFile(), 4);
		AtomicInteger reports = new AtomicInteger();
		for (int i = 0; i < 2; i++) {
			try (InputStream input = pool.open(
				"assets/minecraft/missing.txt",
				() -> new ByteArrayInputStream("fallback".getBytes(StandardCharsets.UTF_8)),
				failure -> reports.incrementAndGet()
			)) {
				assertEquals("fallback", new String(input.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		assertEquals(1, reports.get());
		pool.close();
	}

	@Test
	void supplierCapturedBeforeCloseCannotReopenPool() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("generation.zip"), 1);
		PackArchiveState state = new PackArchiveState();
		InputStreamSupplier supplier = state.pooledSupplier(
			archive.toFile(),
			2,
			"assets/minecraft/textures/a.txt",
			() -> new ByteArrayInputStream("fallback".getBytes(StandardCharsets.UTF_8)),
			failure -> {}
		);
		try (InputStream input = supplier.get()) {
			assertEquals("alpha", new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
		state.close();
		try (InputStream input = supplier.get()) {
			assertEquals("fallback", new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
