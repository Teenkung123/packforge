package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackArchiveStateTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void buildsOnceAcrossConcurrentReaders() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("concurrent.zip"), 1_000);
		AtomicInteger builds = new AtomicInteger();
		PackArchiveState state = new PackArchiveState(zipFile -> {
			builds.incrementAndGet();
			return PackIndex.build(zipFile);
		});
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			ExecutorService executor = Executors.newFixedThreadPool(12);
			try {
				List<Callable<PackIndex>> calls = new ArrayList<>();
				for (int i = 0; i < 48; i++) {
					calls.add(() -> state.index(zipFile, archive.toString(), failure -> {}));
				}
				List<Future<PackIndex>> results = executor.invokeAll(calls);
				PackIndex first = results.get(0).get();
				assertNotNull(first);
				for (Future<PackIndex> result : results) {
					assertSame(first, result.get());
				}
				assertEquals(1, builds.get());
				assertEquals(PackArchiveState.IndexStatus.READY, state.status());
			} finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	void cachesFailureUntilInvalidatedAndDoesNotCatchError() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("failure.zip"), 1);
		AtomicInteger attempts = new AtomicInteger();
		AtomicInteger reports = new AtomicInteger();
		PackArchiveState state = new PackArchiveState(zipFile -> {
			attempts.incrementAndGet();
			throw new IllegalStateException("synthetic failure");
		});
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			assertNull(state.index(zipFile, "failure.zip", failure -> reports.incrementAndGet()));
			assertNull(state.index(zipFile, "failure.zip", failure -> reports.incrementAndGet()));
			assertEquals(1, attempts.get());
			assertEquals(1, reports.get());
			assertEquals(PackArchiveState.IndexStatus.FAILED, state.status());

			state.invalidate();
			assertNull(state.index(zipFile, "failure.zip", failure -> reports.incrementAndGet()));
			assertEquals(2, attempts.get());
			assertEquals(2, reports.get());
		}

		PackArchiveState fatalState = new PackArchiveState(zipFile -> {
			throw new AssertionError("must propagate");
		});
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			assertThrows(AssertionError.class, () -> fatalState.index(zipFile, "failure.zip", failure -> {}));
		}
	}

	@Test
	void closeIsPermanentAndIdempotent() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("closed.zip"), 1);
		PackArchiveState state = new PackArchiveState();
		state.close();
		state.close();
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			assertNull(state.index(zipFile, "closed.zip", failure -> {}));
		}
	}
}
