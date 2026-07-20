package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackIndexTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void indexesExactPrefixOverlayAndNamespaces() throws IOException {
		Path archive = DeterministicZipFixture.create(temporaryDirectory.resolve("fixture.zip"), 20_000);
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);

			assertNotNull(index.entryFor("assets/minecraft/textures/a.txt"));
			try (InputStream input = zipFile.getInputStream(index.entryFor("assets/minecraft/textures/a.txt"))) {
				assertEquals("alpha", new String(input.readAllBytes(), StandardCharsets.UTF_8));
			}

			List<String> texturePaths = new ArrayList<>();
			index.forEachFileWithPrefix("assets/minecraft/textures/", entry -> texturePaths.add(entry.path()));
			assertTrue(texturePaths.contains("assets/minecraft/textures/a.txt"));
			List<String> sortedTexturePaths = new ArrayList<>(texturePaths);
			sortedTexturePaths.sort(String::compareTo);
			assertEquals(sortedTexturePaths, texturePaths);

			List<String> overlayPaths = new ArrayList<>();
			index.forEachFileWithPrefix("overlay/assets/example/", entry -> overlayPaths.add(entry.path()));
			assertEquals(List.of(
				"overlay/assets/example/textures/duplicate.txt",
				"overlay/assets/example/textures/overlay.txt"
			), overlayPaths);

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
	void preservesDuplicateEnumerationAndVanillaCanonicalLookup() throws IOException {
		Path archive = DeterministicZipFixture.createWithDuplicateEntry(temporaryDirectory.resolve("duplicates.zip"));
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			PackIndex index = PackIndex.build(zipFile);
			ZipEntry vanillaEntry = zipFile.getEntry("assets/minecraft/textures/duplicate.txt");
			ZipEntry indexedEntry = index.entryFor("assets/minecraft/textures/duplicate.txt");
			assertNotNull(vanillaEntry);
			assertNotNull(indexedEntry);
			try (InputStream vanilla = zipFile.getInputStream(vanillaEntry);
				 InputStream indexed = zipFile.getInputStream(indexedEntry)) {
				assertEquals(
					new String(vanilla.readAllBytes(), StandardCharsets.UTF_8),
					new String(indexed.readAllBytes(), StandardCharsets.UTF_8)
				);
			}

			List<PackIndex.IndexedEntry> duplicates = new ArrayList<>();
			index.forEachFileWithPrefix("assets/minecraft/textures/duplicate.txt", duplicates::add);
			assertEquals(2, duplicates.size());
			assertTrue(duplicates.stream().allMatch(PackIndex.IndexedEntry::duplicatePath));
			assertTrue(index.hasDuplicatePath("assets/minecraft/textures/duplicate.txt"));
			assertEquals(1, index.duplicatePathCount());
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
}
