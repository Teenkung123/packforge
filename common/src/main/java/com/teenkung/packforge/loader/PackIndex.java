package com.teenkung.packforge.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Immutable, Minecraft-independent index of one ZIP resource pack.
 *
 * <p>The index retains central-directory entries, including directories and
 * duplicate names. Exact lookups use the same canonical entry selected by
 * {@link ZipFile#getEntry(String)}, while prefix iteration keeps duplicate
 * file entries in their original relative order.</p>
 */
public final class PackIndex {
	private final ZipFile zipFile;
	private final IndexedEntry[] sortedEntries;
	private final String[] sortedPaths;
	private final Map<String, ZipEntry> exactLookup;
	private final Set<String> duplicatePaths;
	private final Map<NamespaceCacheKey, NamespaceResult> namespaceCache = new ConcurrentHashMap<>();

	private PackIndex(
		ZipFile zipFile,
		IndexedEntry[] sortedEntries,
		String[] sortedPaths,
		Map<String, ZipEntry> exactLookup,
		Set<String> duplicatePaths
	) {
		this.zipFile = zipFile;
		this.sortedEntries = sortedEntries;
		this.sortedPaths = sortedPaths;
		this.exactLookup = exactLookup;
		this.duplicatePaths = duplicatePaths;
	}

	public static PackIndex build(ZipFile zipFile) {
		Objects.requireNonNull(zipFile, "zipFile");
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		List<RawEntry> rawEntries = new ArrayList<>();
		Map<String, Integer> occurrenceCounts = new HashMap<>();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			String path = entry.getName();
			rawEntries.add(new RawEntry(path, entry));
			occurrenceCounts.merge(path, 1, Integer::sum);
		}

		Set<String> duplicates = new HashSet<>();
		Map<String, ZipEntry> exact = new HashMap<>(Math.max(16, occurrenceCounts.size() * 2));
		for (Map.Entry<String, Integer> occurrence : occurrenceCounts.entrySet()) {
			String path = occurrence.getKey();
			ZipEntry canonical = zipFile.getEntry(path);
			if (canonical != null) {
				exact.put(path, canonical);
			}
			if (occurrence.getValue() > 1) {
				duplicates.add(path);
			}
		}

		// Object-array sorting is stable, so equal paths retain ZIP enumeration order.
		rawEntries.sort((left, right) -> left.path.compareTo(right.path));
		IndexedEntry[] indexed = new IndexedEntry[rawEntries.size()];
		String[] paths = new String[rawEntries.size()];
		for (int i = 0; i < rawEntries.size(); i++) {
			RawEntry raw = rawEntries.get(i);
			paths[i] = raw.path;
			indexed[i] = new IndexedEntry(raw.path, raw.entry, duplicates.contains(raw.path));
		}

		return new PackIndex(
			zipFile,
			indexed,
			paths,
			Collections.unmodifiableMap(exact),
			Collections.unmodifiableSet(duplicates)
		);
	}

	public ZipFile zipFile() {
		return zipFile;
	}

	/** Returns the entry selected by {@link ZipFile#getEntry(String)}. */
	public ZipEntry entryFor(String fullPath) {
		return exactLookup.get(fullPath);
	}

	/**
	 * Iterates non-directory entries with the requested prefix. Duplicate entry
	 * names are retained. Ordering is lexical by path and stable for duplicates.
	 */
	public void forEachFileWithPrefix(String prefix, Consumer<IndexedEntry> out) {
		Objects.requireNonNull(prefix, "prefix");
		Objects.requireNonNull(out, "out");
		int index = lowerBound(prefix);
		for (int i = index; i < sortedEntries.length; i++) {
			IndexedEntry entry = sortedEntries[i];
			if (!entry.path.startsWith(prefix)) {
				break;
			}
			if (!entry.zipEntry.isDirectory()) {
				out.accept(entry);
			}
		}
	}

	/** Compatibility convenience for adapters that do not need duplicate metadata. */
	public void forEachWithPrefix(String prefix, BiConsumer<String, ZipEntry> out) {
		Objects.requireNonNull(out, "out");
		forEachFileWithPrefix(prefix, entry -> out.accept(entry.path, entry.zipEntry));
	}

	public NamespaceResult namespacesFor(String typePrefix, ResourceNamePolicy policy) {
		Objects.requireNonNull(typePrefix, "typePrefix");
		Objects.requireNonNull(policy, "policy");
		NamespaceCacheKey key = new NamespaceCacheKey(typePrefix, policy);
		return namespaceCache.computeIfAbsent(key, ignored -> buildNamespaces(typePrefix, policy));
	}

	/** Uses the current Minecraft namespace policy. */
	public Set<String> namespacesFor(String typePrefix) {
		return namespacesFor(typePrefix, ResourceNamePolicy.current()).valid();
	}

	public boolean hasDuplicatePath(String path) {
		return duplicatePaths.contains(path);
	}

	/** Number of unique paths addressable through exact lookup. */
	public int size() {
		return exactLookup.size();
	}

	/** Number of central-directory entries, including directories and duplicates. */
	public int entryCount() {
		return sortedEntries.length;
	}

	public int duplicatePathCount() {
		return duplicatePaths.size();
	}

	private NamespaceResult buildNamespaces(String typePrefix, ResourceNamePolicy policy) {
		int index = lowerBound(typePrefix);
		LinkedHashSet<String> valid = new LinkedHashSet<>();
		LinkedHashSet<String> invalid = new LinkedHashSet<>();
		for (int i = index; i < sortedPaths.length; i++) {
			String path = sortedPaths[i];
			if (!path.startsWith(typePrefix)) {
				break;
			}
			int candidateStart = typePrefix.length();
			if (policy.omitEmptyNamespaceSegments()) {
				while (candidateStart < path.length() && path.charAt(candidateStart) == '/') {
					candidateStart++;
				}
			}
			int slash = path.indexOf('/', candidateStart);
			String candidate = slash == -1
				? path.substring(candidateStart)
				: path.substring(candidateStart, slash);
			if (candidate.isEmpty()) {
				continue;
			}
			if (policy.isValidNamespace(candidate)) {
				valid.add(candidate);
			} else {
				invalid.add(candidate);
			}
		}
		return new NamespaceResult(immutableSet(valid), immutableSet(invalid));
	}

	private int lowerBound(String key) {
		int low = 0;
		int high = sortedPaths.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (sortedPaths[middle].compareTo(key) < 0) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	private static Set<String> immutableSet(LinkedHashSet<String> values) {
		return Collections.unmodifiableSet(new LinkedHashSet<>(values));
	}

	public record IndexedEntry(String path, ZipEntry zipEntry, boolean duplicatePath) {
		public IndexedEntry {
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(zipEntry, "zipEntry");
		}
	}

	public record NamespaceResult(Set<String> valid, Set<String> invalid) {
		public NamespaceResult {
			Objects.requireNonNull(valid, "valid");
			Objects.requireNonNull(invalid, "invalid");
		}
	}

	private record RawEntry(String path, ZipEntry entry) {}

	private record NamespaceCacheKey(String prefix, ResourceNamePolicy policy) {}
}
