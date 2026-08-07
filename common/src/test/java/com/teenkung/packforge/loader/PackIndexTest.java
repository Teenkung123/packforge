package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackIndexTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void handlesEmptyAndSingleArchives() throws IOException {
		Path emptyArchive = temporaryDirectory.resolve("empty.zip");
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(emptyArchive))) {
		}
		try (ZipFile zipFile = new ZipFile(emptyArchive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			assertEquals(0, index.entryCount());
			assertEquals(0, index.size());
			assertTrue(index.entryFor("missing") == null);
			assertEquals(List.of(), lexicalFiles(index, ""));
			assertEquals(List.of(), centralNames(index, ""));
		}

		Path singleArchive = createArchive(temporaryDirectory.resolve("single.zip"), "single.txt");
		try (ZipFile zipFile = new ZipFile(singleArchive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			assertEquals(1, index.entryCount());
			assertEquals(1, index.size());
			assertEquals(List.of("single.txt"), lexicalFiles(index, ""));
			assertEquals(List.of("single.txt"), centralNames(index, ""));
		}
	}

	@Test
	void indexesTwentyThousandEntriesWithExactAndPrefixParity() throws IOException {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("fixture.zip"), 20_000);
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);

			assertTrue(index.entryCount() >= 20_000);
			assertNotNull(index.entryFor("assets/minecraft/textures/a.txt"));
			try (InputStream input = zipFile.getInputStream(index.entryFor("assets/minecraft/textures/a.txt"))) {
				assertEquals("alpha", new String(input.readAllBytes(), StandardCharsets.UTF_8));
			}

			List<String> texturePaths = lexicalFiles(index, "assets/minecraft/textures/");
			List<String> sortedTexturePaths = new ArrayList<>(texturePaths);
			sortedTexturePaths.sort(String::compareTo);
			assertEquals(sortedTexturePaths, texturePaths);
			assertTrue(texturePaths.contains("assets/minecraft/textures/a.txt"));

			assertEquals(
				List.of(
					"overlay/assets/example/textures/duplicate.txt",
					"overlay/assets/example/textures/overlay.txt"
				),
				lexicalFiles(index, "overlay/assets/example/")
			);

			PackIndex.NamespaceResult current = index.namespacesFor("assets/", ResourceNamePolicy.current());
			assertTrue(current.valid().containsAll(Set.of("minecraft", "empty_namespace", "generated0", "generated7")));
			assertTrue(current.invalid().containsAll(Set.of("BadNamespace", "foo@bar")));

			PackIndex.NamespaceResult legacy = index.namespacesFor("assets/", ResourceNamePolicy.legacy1201());
			assertTrue(legacy.valid().contains("foo@bar"));
			assertTrue(legacy.valid().contains("textures"));
			assertFalse(legacy.valid().contains("BadNamespace"));
			assertFalse(ResourceNamePolicy.current().isValidPath("textures/Bad Path.png"));
		}
	}

	@Test
	void runtimeFixtureContainsOnlyValidResourceNames() throws IOException {
		Path archive = DeterministicZipFixture.createRuntimeCompatible(
			temporaryDirectory.resolve("runtime-fixture.zip"),
			100,
			DeterministicZipFixture.defaultPackMetadata()
		);
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			ResourceNamePolicy policy = ResourceNamePolicy.current();
			PackIndex.NamespaceResult namespaces = PackIndex.build(zipFile)
				.namespacesFor("assets/", policy);
			assertTrue(namespaces.invalid().isEmpty());
			assertFalse(namespaces.valid().isEmpty());

			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || entry.getName().equals("pack.mcmeta")) {
					continue;
				}
				int assetsStart = entry.getName().indexOf("assets/");
				assertTrue(assetsStart == 0 || assetsStart > 0 && entry.getName().charAt(assetsStart - 1) == '/');
				String resourceName = entry.getName().substring(assetsStart + "assets/".length());
				int namespaceEnd = resourceName.indexOf('/');
				assertTrue(namespaceEnd > 0);
				assertTrue(policy.isValidNamespace(resourceName.substring(0, namespaceEnd)), entry.getName());
				assertTrue(policy.isValidPath(resourceName.substring(namespaceEnd + 1)), entry.getName());
			}
		}
	}

	@Test
	void preservesDuplicateWinnerAndStableDuplicateOrder() throws IOException {
		Path archive = DeterministicZipFixture.createWithDuplicateEntry(temporaryDirectory.resolve("duplicates.zip"));
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			String path = "assets/minecraft/textures/duplicate.txt";
			ZipEntry vanillaEntry = zipFile.getEntry(path);
			ZipEntry indexedEntry = index.entryFor(path);
			assertNotNull(vanillaEntry);
			assertNotNull(indexedEntry);
			assertEquals(read(zipFile, vanillaEntry), read(zipFile, indexedEntry));

			List<Long> lexicalSizes = new ArrayList<>();
			List<Boolean> duplicateFlags = new ArrayList<>();
			index.forEachFileWithPrefix(path, entry -> {
				lexicalSizes.add(entry.zipEntry().getSize());
				duplicateFlags.add(entry.duplicatePath());
			});
			assertEquals(List.of(5L, 6L), lexicalSizes);
			assertEquals(List.of(true, true), duplicateFlags);
			assertEquals(2, lexicalSizes.size());
			assertTrue(index.hasDuplicatePath(path));
			assertEquals(1, index.duplicatePathCount());
			assertTrue(index.entriesWithPrefix(path).hasMoreElements());
		}
	}

	@Test
	void resolvesCanonicalEntriesOnlyForActualDuplicateNames() throws IOException {
		Path uniqueArchive = DeterministicZipFixture.create(temporaryDirectory.resolve("unique.zip"), 100);
		try (CountingZipFile zipFile = new CountingZipFile(uniqueArchive)) {
			PackIndex.build(zipFile);
			assertEquals(0, zipFile.getEntryCalls());
		}

		Path duplicateArchive = DeterministicZipFixture.createWithDuplicateEntry(
			temporaryDirectory.resolve("counted-duplicates.zip")
		);
		try (CountingZipFile zipFile = new CountingZipFile(duplicateArchive)) {
			PackIndex index = PackIndex.build(zipFile);
			assertEquals(1, zipFile.getEntryCalls());
			assertEquals(1, index.duplicatePathCount());
		}
	}

	@Test
	void separatesLexicalAndCentralPrefixSemantics() throws IOException {
		Path archive = createArchive(
			temporaryDirectory.resolve("orders.zip"),
			"assets/z.txt",
			"assets/dir/",
			"assets/a.txt",
			"assets/é.txt"
		);
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			assertEquals(
				List.of("assets/a.txt", "assets/z.txt", "assets/é.txt"),
				lexicalFiles(index, "assets/")
			);
			assertEquals(
				List.of("assets/a.txt", "assets/dir/", "assets/z.txt", "assets/é.txt"),
				lexicalAll(index, "assets/")
			);
			assertEquals(
				List.of("assets/z.txt", "assets/dir/", "assets/a.txt", "assets/é.txt"),
				centralNames(index, "assets/")
			);
		}
	}

	@Test
	void handlesNonAsciiPrefixEndWithoutSentinel() throws IOException {
		Path archive = createArchive(
			temporaryDirectory.resolve("unicode.zip"),
			"assets/e.txt",
			"assets/é.txt",
			"assets/éclair.txt",
			"assets/ê.txt",
			"assets/zzz.txt"
		);
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			assertEquals(List.of("assets/é.txt", "assets/éclair.txt"), lexicalFiles(index, "assets/é"));
			assertEquals(List.of("assets/é.txt", "assets/éclair.txt"), centralNames(index, "assets/é"));
			assertEquals(List.of("assets/ê.txt"), lexicalFiles(index, "assets/ê"));
		}
	}

	@Test
	void boundsPrefixAndNamespaceCachesAndComputesAfterFull() throws IOException {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("cache.zip"), 100);
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			for (int i = 0; i < PackIndex.MAX_PREFIX_CACHE_ENTRIES + 20; i++) {
				assertEquals(List.of(), lexicalFiles(index, "missing-prefix-" + i + "/"));
			}
			for (int i = 0; i < PackIndex.MAX_NAMESPACE_CACHE_ENTRIES + 20; i++) {
				assertTrue(index.namespacesFor("missing-namespace-" + i + "/", ResourceNamePolicy.current()).valid().isEmpty());
			}
			assertEquals(PackIndex.MAX_PREFIX_CACHE_ENTRIES, index.prefixCacheSize());
			assertEquals(PackIndex.MAX_NAMESPACE_CACHE_ENTRIES, index.namespaceCacheSize());
			assertTrue(index.cachedPrefixOrdinalCount() <= PackIndex.MAX_PREFIX_CACHE_ORDINALS);

			index.invalidateCaches();
			assertFalse(index.cachesEnabled());
			assertEquals(0, index.prefixCacheSize());
			assertEquals(0, index.namespaceCacheSize());
			assertEquals(0, index.cachedPrefixOrdinalCount());
			assertTrue(lexicalFiles(index, "assets/minecraft/").contains("assets/minecraft/font/default.json"));
			assertEquals(0, index.prefixCacheSize());
		}
	}

	@Test
	void supportsConcurrentCachedReads() throws Exception {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("parallel.zip"), 1_000);
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			ExecutorService executor = Executors.newFixedThreadPool(8);
			try {
				List<Callable<String>> calls = new ArrayList<>();
				for (int i = 0; i < 32; i++) {
					calls.add(() -> {
						List<String> files = lexicalFiles(index, "assets/generated3/");
						Set<String> namespaces = index.namespacesFor("assets/", ResourceNamePolicy.current()).valid();
						return files.size() + ":" + namespaces.size() + ":" + centralNames(index, "assets/minecraft/").size();
					});
				}
				List<Future<String>> results = executor.invokeAll(calls);
				String first = results.get(0).get();
				assertTrue(first.startsWith("125:"));
				for (Future<String> result : results) {
					assertEquals(first, result.get());
				}
				assertTrue(index.prefixCacheSize() <= PackIndex.MAX_PREFIX_CACHE_ENTRIES);
				assertTrue(index.namespaceCacheSize() <= PackIndex.MAX_NAMESPACE_CACHE_ENTRIES);
			} finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	void validatesResourceNamesWithoutMinecraftClasses() {
		assertTrue(ResourceNamePolicy.current().isValidNamespace("valid_namespace-1.2"));
		assertFalse(ResourceNamePolicy.current().isValidNamespace("Invalid"));
		assertFalse(ResourceNamePolicy.current().isValidNamespace("bad@name"));
		assertTrue(ResourceNamePolicy.current().isValidPath("textures/gui/example.png"));
		assertFalse(ResourceNamePolicy.current().isValidPath("textures/GUI/example.png"));
		assertTrue(ResourceNamePolicy.legacy1201().isValidNamespace("lower@legacy"));
	}

	private static List<String> lexicalFiles(PackIndex index, String prefix) {
		List<String> names = new ArrayList<>();
		index.forEachFileWithPrefix(prefix, entry -> names.add(entry.path()));
		return names;
	}

	private static List<String> lexicalAll(PackIndex index, String prefix) {
		List<String> names = new ArrayList<>();
		index.forEachEntryWithPrefix(prefix, entry -> names.add(entry.path()));
		return names;
	}

	private static List<String> centralNames(PackIndex index, String prefix) {
		List<String> names = new ArrayList<>();
		Enumeration<ZipEntry> entries = index.entriesWithPrefix(prefix);
		while (entries.hasMoreElements()) {
			names.add(entries.nextElement().getName());
		}
		return names;
	}

	private static Path createArchive(Path path, String... names) throws IOException {
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
			for (String name : names) {
				ZipEntry entry = new ZipEntry(name);
				entry.setTime(0L);
				output.putNextEntry(entry);
				if (!name.endsWith("/")) {
					output.write(name.getBytes(StandardCharsets.UTF_8));
				}
				output.closeEntry();
			}
		}
		return path;
	}

	private static String read(ZipFile zipFile, ZipEntry entry) throws IOException {
		try (InputStream input = zipFile.getInputStream(entry)) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static final class CountingZipFile extends ZipFile {
		private final AtomicInteger getEntryCalls = new AtomicInteger();

		private CountingZipFile(Path path) throws IOException {
			super(path.toFile());
		}

		@Override
		public ZipEntry getEntry(String name) {
			getEntryCalls.incrementAndGet();
			return super.getEntry(name);
		}

		private int getEntryCalls() {
			return getEntryCalls.get();
		}
	}

}
