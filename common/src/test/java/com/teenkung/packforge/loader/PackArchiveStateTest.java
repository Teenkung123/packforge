package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
	void invalidationDisablesOldCachesWithoutBreakingExistingReaders() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("invalidate.zip"), 100);
		PackArchiveState state = new PackArchiveState();
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex oldIndex = state.index(zipFile, "invalidate.zip", failure -> {});
			assertNotNull(oldIndex);
			oldIndex.forEachFileWithPrefix("assets/minecraft/", ignored -> {});
			oldIndex.namespacesFor("assets/", ResourceNamePolicy.current());
			assertTrue(oldIndex.prefixCacheSize() > 0);
			assertTrue(oldIndex.namespaceCacheSize() > 0);

			state.invalidate();
			assertFalse(oldIndex.cachesEnabled());
			assertEquals(0, oldIndex.prefixCacheSize());
			assertEquals(0, oldIndex.namespaceCacheSize());
			List<String> names = new ArrayList<>();
			oldIndex.forEachFileWithPrefix("assets/minecraft/", entry -> names.add(entry.path()));
			assertTrue(names.contains("assets/minecraft/font/default.json"));

			PackIndex newIndex = state.index(zipFile, "invalidate.zip", failure -> {});
			assertNotNull(newIndex);
			assertTrue(newIndex.cachesEnabled());
			assertTrue(newIndex != oldIndex);
		}
	}

	@Test
	void closeDisablesExistingCachesAndIsPermanentAndIdempotent() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("closed.zip"), 1);
		PackArchiveState state = new PackArchiveState();
		PackIndex oldIndex;
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			oldIndex = state.index(zipFile, "closed.zip", failure -> {});
			assertNotNull(oldIndex);
			oldIndex.forEachFileWithPrefix("assets/", ignored -> {});
			assertTrue(oldIndex.prefixCacheSize() > 0);
		}
		state.close();
		state.close();
		assertFalse(oldIndex.cachesEnabled());
		assertEquals(0, oldIndex.prefixCacheSize());
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			assertNull(state.index(zipFile, "closed.zip", failure -> {}));
		}
	}

	@Test
	void duplicateArchivesCacheDeliberateBypassWithoutWarningsOrRetainedIndexCaches() throws Exception {
		Path archive = DeterministicZipFixture.createWithDuplicateEntry(temporaryDirectory.resolve("duplicates.zip"));
		AtomicInteger builds = new AtomicInteger();
		AtomicInteger reports = new AtomicInteger();
		AtomicReference<PackIndex> discarded = new AtomicReference<>();
		try (PackArchiveState state = new PackArchiveState(zip -> {
			builds.incrementAndGet();
			PackIndex index = PackIndex.build(zip);
			index.entriesWithPrefix("assets/");
			discarded.set(index);
			return index;
		}); ZipFile zip = new ZipFile(archive.toFile())) {
			for (int i = 0; i < 12; i++) assertNull(state.index(zip, "duplicates.zip", failure -> reports.incrementAndGet()));
			assertEquals(1, builds.get());
			assertEquals(0, reports.get());
			assertEquals(PackArchiveState.IndexStatus.BYPASSED_DUPLICATES, state.status());
			assertNull(state.indexFailure());
			assertSame(zip, state.indexedZipFile());
			assertFalse(discarded.get().cachesEnabled());
			assertEquals(0, discarded.get().prefixCacheSize());
			state.invalidate();
			assertEquals(PackArchiveState.IndexStatus.UNINITIALIZED, state.status());
			assertNull(state.index(zip, "duplicates.zip", failure -> reports.incrementAndGet()));
			assertEquals(2, builds.get());
			assertEquals(0, reports.get());
		}
	}

	@Test
	void duplicateBypassPreservesDirectEnumerationAndUniqueLookupZipSideEffects() throws Exception {
		Path archive = DeterministicZipFixture.createWithDuplicateEntry(temporaryDirectory.resolve("side-effects.zip"));
		String duplicate = "assets/minecraft/textures/duplicate.txt";
		String unique = "assets/minecraft/textures/other.txt";
		try (PackArchiveState state = new PackArchiveState(); ZipFile zip = new ZipFile(archive.toFile())) {
			assertNull(state.index(zip, "side-effects.zip", failure -> { throw new AssertionError(failure); }));

			zip.entries().nextElement();
			assertEquals("second", read(zip, lookup(state, zip, duplicate)),
				"Direct fallback must restore the same canonical lookup state as vanilla");

			zip.getEntry(duplicate);
			assertEquals("first", read(zip, enumerate(state, zip).nextElement()),
				"Enumeration fallback must advance Java's state to the first duplicate");

			ZipEntry pending = zip.getEntry(duplicate);
			zip.entries().nextElement();
			lookup(state, zip, unique);
			assertEquals("second", read(zip, pending),
				"Even unique lookups must preserve their side effect on pending duplicate reads");
		}
	}

	@Test
	void duplicateBypassIsPerHandleAndDoesNotDisableAReopenedSafeArchive() throws Exception {
		Path duplicateArchive = DeterministicZipFixture.createWithDuplicateEntry(temporaryDirectory.resolve("duplicate-reopen.zip"));
		Path safeArchive = DeterministicZipFixture.create(temporaryDirectory.resolve("safe.zip"), 3);
		AtomicInteger builds = new AtomicInteger();
		try (PackArchiveState state = new PackArchiveState(zip -> { builds.incrementAndGet(); return PackIndex.build(zip); })) {
			try (ZipFile first = new ZipFile(duplicateArchive.toFile())) {
				assertNull(state.index(first, "duplicate-reopen.zip", failure -> {}));
				assertEquals(PackArchiveState.IndexStatus.BYPASSED_DUPLICATES, state.status());
			}
			try (ZipFile reopened = new ZipFile(duplicateArchive.toFile())) {
				assertNull(state.index(reopened, "duplicate-reopen.zip", failure -> {}));
				assertEquals(2, builds.get(), "A new ZIP handle gets its own classification");
			}
			try (ZipFile safe = new ZipFile(safeArchive.toFile())) {
				PackIndex index = state.index(safe, "safe.zip", failure -> {});
				assertNotNull(index);
				assertTrue(index.cachesEnabled());
				assertEquals(PackArchiveState.IndexStatus.READY, state.status());
				assertSame(index, state.index(safe, "safe.zip", failure -> {}));
				assertEquals(3, builds.get());
			}
		}
	}

	private static ZipEntry lookup(PackArchiveState state, ZipFile zip, String path) {
		PackIndex index = state.index(zip, zip.getName(), failure -> { throw new AssertionError(failure); });
		return index == null ? zip.getEntry(path) : index.entryFor(path);
	}

	private static Enumeration<? extends ZipEntry> enumerate(PackArchiveState state, ZipFile zip) {
		PackIndex index = state.index(zip, zip.getName(), failure -> { throw new AssertionError(failure); });
		return index == null ? zip.entries() : index.entriesWithPrefix("");
	}

	private static String read(ZipFile zip, ZipEntry entry) throws IOException {
		try (var input = zip.getInputStream(entry)) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
