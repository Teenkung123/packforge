package com.teenkung.packforge.loader;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Immutable, Minecraft-independent index of one ZIP resource pack.
 *
 * <p>The index retains the ZIP central-directory order in one compact entry
 * array. A separate primitive ordinal array provides stable lexical searches
 * without retaining a second path array or one temporary sort wrapper per
 * entry. Exact lookups preserve {@link ZipFile#getEntry(String)} semantics for
 * duplicate names.</p>
 */
public final class PackIndex {
	/** Maximum number of distinct lexical prefix results retained by an index. */
	public static final int MAX_PREFIX_CACHE_ENTRIES = 64;
	/** Maximum total retained prefix-result ordinals (one MiB of primitive storage). */
	public static final int MAX_PREFIX_CACHE_ORDINALS = 262_144;
	/** Maximum number of distinct namespace/policy results retained by an index. */
	public static final int MAX_NAMESPACE_CACHE_ENTRIES = 32;

	private static final int MAX_PREALLOCATED_ENTRIES = 1 << 20;
	private static final int MIN_GROWTH = 8;

	private final ZipFile zipFile;
	private final IndexedEntry[] entriesInCentralOrder;
	private final int[] sortedOrdinalsByPath;
	private final Map<String, ZipEntry> exactLookup;
	private final Set<String> duplicatePaths;
	private final Object cacheLock = new Object();
	private final Map<PrefixCacheKey, int[]> prefixCache = new HashMap<>();
	private final Map<NamespaceCacheKey, NamespaceResult> namespaceCache = new HashMap<>();
	private int cachedPrefixOrdinals;
	private volatile boolean cachesEnabled = true;

	private PackIndex(
		ZipFile zipFile,
		IndexedEntry[] entriesInCentralOrder,
		int[] sortedOrdinalsByPath,
		Map<String, ZipEntry> exactLookup,
		Set<String> duplicatePaths
	) {
		this.zipFile = zipFile;
		this.entriesInCentralOrder = entriesInCentralOrder;
		this.sortedOrdinalsByPath = sortedOrdinalsByPath;
		this.exactLookup = exactLookup;
		this.duplicatePaths = duplicatePaths;
	}

	/**
	 * Builds an index with one central-directory enumeration.
	 *
	 * <p>{@code ZipFile.size()} is only used as a bounded allocation hint. The
	 * array still grows safely if a provider reports an unexpected size. The
	 * exact map is populated with {@link Map#putIfAbsent(Object, Object)} while
	 * enumerating; only names that collided are resolved again through
	 * {@link ZipFile#getEntry(String)}.</p>
	 */
	public static PackIndex build(ZipFile zipFile) {
		Objects.requireNonNull(zipFile, "zipFile");

		int estimatedEntries = zipFile.size();
		IndexedEntry[] centralEntries = new IndexedEntry[initialEntryCapacity(estimatedEntries)];
		Map<String, ZipEntry> exact = new HashMap<>(initialMapCapacity(estimatedEntries));
		Set<String> duplicates = new HashSet<>();
		int entryCount = 0;

		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			String path = entry.getName();
			if (entryCount == centralEntries.length) {
				centralEntries = Arrays.copyOf(centralEntries, grownCapacity(entryCount));
			}
			centralEntries[entryCount++] = new IndexedEntry(path, entry, false);
			if (exact.putIfAbsent(path, entry) != null) {
				duplicates.add(path);
			}
		}

		if (entryCount != centralEntries.length) {
			centralEntries = Arrays.copyOf(centralEntries, entryCount);
		}

		if (!duplicates.isEmpty()) {
			for (int ordinal = 0; ordinal < centralEntries.length; ordinal++) {
				IndexedEntry entry = centralEntries[ordinal];
				if (duplicates.contains(entry.path())) {
					centralEntries[ordinal] = new IndexedEntry(entry.path(), entry.zipEntry(), true);
				}
			}
			for (String duplicatePath : duplicates) {
				ZipEntry canonical = zipFile.getEntry(duplicatePath);
				if (canonical != null) {
					exact.put(duplicatePath, canonical);
				}
			}
		}

		int[] sortedOrdinals = new int[entryCount];
		for (int ordinal = 0; ordinal < entryCount; ordinal++) {
			sortedOrdinals[ordinal] = ordinal;
		}
		if (entryCount > 1) {
			stableSortOrdinals(sortedOrdinals, new int[entryCount], centralEntries, 0, entryCount);
		}

		return new PackIndex(
			zipFile,
			centralEntries,
			sortedOrdinals,
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
		for (int ordinal : prefixOrdinals(prefix, PrefixMode.FILES_LEXICAL)) {
			out.accept(entriesInCentralOrder[ordinal]);
		}
	}

	/** Iterates all matching entries in stable lexical order, including directories. */
	public void forEachEntryWithPrefix(String prefix, Consumer<IndexedEntry> out) {
		Objects.requireNonNull(prefix, "prefix");
		Objects.requireNonNull(out, "out");
		for (int ordinal : prefixOrdinals(prefix, PrefixMode.ALL_LEXICAL)) {
			out.accept(entriesInCentralOrder[ordinal]);
		}
	}

	/** Compatibility convenience for adapters that do not need duplicate metadata. */
	public void forEachWithPrefix(String prefix, BiConsumer<String, ZipEntry> out) {
		Objects.requireNonNull(out, "out");
		forEachFileWithPrefix(prefix, entry -> out.accept(entry.path(), entry.zipEntry()));
	}

	/**
	 * Adapts indexed entries to the central-order enumeration shape used by
	 * vanilla FilePack hooks. Directories and duplicate names are preserved.
	 */
	public Enumeration<ZipEntry> entriesWithPrefix(String prefix) {
		Objects.requireNonNull(prefix, "prefix");
		int[] ordinals = prefixOrdinals(prefix, PrefixMode.ALL_CENTRAL);
		return new Enumeration<>() {
			private int cursor;

			@Override
			public boolean hasMoreElements() {
				return cursor < ordinals.length;
			}

			@Override
			public ZipEntry nextElement() {
				if (cursor >= ordinals.length) {
					throw new NoSuchElementException();
				}
				return entriesInCentralOrder[ordinals[cursor++]].zipEntry();
			}
		};
	}

	/** Alias named for callers that want the ordering guarantee in the method name. */
	public Enumeration<ZipEntry> centralEntriesWithPrefix(String prefix) {
		return entriesWithPrefix(prefix);
	}

	public NamespaceResult namespacesFor(String typePrefix, ResourceNamePolicy policy) {
		Objects.requireNonNull(typePrefix, "typePrefix");
		Objects.requireNonNull(policy, "policy");
		NamespaceCacheKey key = new NamespaceCacheKey(typePrefix, policy);
		NamespaceResult cached = cachedNamespace(key);
		if (cached != null) {
			return cached;
		}
		NamespaceResult result = buildNamespaces(typePrefix, policy);
		cacheNamespace(key, result);
		return result;
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
		return entriesInCentralOrder.length;
	}

	public int duplicatePathCount() {
		return duplicatePaths.size();
	}

	/** Current number of retained lexical prefix results, for diagnostics/tests. */
	public int prefixCacheSize() {
		synchronized (cacheLock) {
			return prefixCache.size();
		}
	}

	/** Current number of retained namespace results, for diagnostics/tests. */
	public int namespaceCacheSize() {
		synchronized (cacheLock) {
			return namespaceCache.size();
		}
	}

	/** Current total retained primitive prefix ordinals, for diagnostics/tests. */
	public int cachedPrefixOrdinalCount() {
		synchronized (cacheLock) {
			return cachedPrefixOrdinals;
		}
	}

	/** Whether this index may retain new query results. */
	public boolean cachesEnabled() {
		return cachesEnabled;
	}

	/**
	 * Clears query caches and disables future cache growth. Existing readers can
	 * continue using the immutable central/sorted arrays; they simply compute
	 * query results without retaining them.
	 */
	public void invalidateCaches() {
		synchronized (cacheLock) {
			cachesEnabled = false;
			prefixCache.clear();
			namespaceCache.clear();
			cachedPrefixOrdinals = 0;
		}
	}

	private int[] prefixOrdinals(String prefix, PrefixMode mode) {
		PrefixCacheKey key = new PrefixCacheKey(prefix, mode);
		int[] cached = cachedPrefix(key);
		if (cached != null) {
			return cached;
		}

		PrefixRange range = prefixRange(prefix);
		int matchCount = 0;
		if (mode != PrefixMode.FILES_LEXICAL) {
			matchCount = range.endExclusive - range.startInclusive;
		} else {
			for (int i = range.startInclusive; i < range.endExclusive; i++) {
				if (!entriesInCentralOrder[sortedOrdinalsByPath[i]].zipEntry().isDirectory()) {
					matchCount++;
				}
			}
		}

		int[] result = new int[matchCount];
		int resultIndex = 0;
		for (int i = range.startInclusive; i < range.endExclusive; i++) {
			int ordinal = sortedOrdinalsByPath[i];
			if (mode != PrefixMode.FILES_LEXICAL || !entriesInCentralOrder[ordinal].zipEntry().isDirectory()) {
				result[resultIndex++] = ordinal;
			}
		}
		if (mode == PrefixMode.ALL_CENTRAL) {
			Arrays.sort(result);
		}
		cachePrefix(key, result);
		return result;
	}

	private NamespaceResult buildNamespaces(String typePrefix, ResourceNamePolicy policy) {
		PrefixRange range = prefixRange(typePrefix);
		LinkedHashSet<String> valid = new LinkedHashSet<>();
		LinkedHashSet<String> invalid = new LinkedHashSet<>();
		String previousCandidate = null;

		for (int i = range.startInclusive; i < range.endExclusive; i++) {
			String path = entriesInCentralOrder[sortedOrdinalsByPath[i]].path();
			int candidateStart = typePrefix.length();
			if (policy.omitEmptyNamespaceSegments()) {
				while (candidateStart < path.length() && path.charAt(candidateStart) == '/') {
					candidateStart++;
				}
			}
			int slash = path.indexOf('/', candidateStart);
			int candidateEnd = slash == -1 ? path.length() : slash;
			if (candidateStart >= candidateEnd) {
				previousCandidate = null;
				continue;
			}

			String candidate;
			int candidateLength = candidateEnd - candidateStart;
			if (previousCandidate != null
				&& previousCandidate.length() == candidateLength
				&& path.regionMatches(candidateStart, previousCandidate, 0, candidateLength)) {
				candidate = previousCandidate;
			} else {
				candidate = path.substring(candidateStart, candidateEnd);
				previousCandidate = candidate;
			}

			if (policy.isValidNamespace(candidate)) {
				valid.add(candidate);
			} else {
				invalid.add(candidate);
			}
		}
		return new NamespaceResult(immutableSet(valid), immutableSet(invalid));
	}

	private PrefixRange prefixRange(String prefix) {
		int start = lowerBound(prefix);
		int low = start;
		int high = sortedOrdinalsByPath.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			String path = entriesInCentralOrder[sortedOrdinalsByPath[middle]].path();
			if (path.startsWith(prefix)) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return new PrefixRange(start, low);
	}

	private int lowerBound(String key) {
		int low = 0;
		int high = sortedOrdinalsByPath.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			String path = entriesInCentralOrder[sortedOrdinalsByPath[middle]].path();
			if (path.compareTo(key) < 0) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	private int[] cachedPrefix(PrefixCacheKey key) {
		if (!cachesEnabled) {
			return null;
		}
		synchronized (cacheLock) {
			return cachesEnabled ? prefixCache.get(key) : null;
		}
	}

	private void cachePrefix(PrefixCacheKey key, int[] result) {
		if (!cachesEnabled) {
			return;
		}
		synchronized (cacheLock) {
			if (cachesEnabled
				&& prefixCache.size() < MAX_PREFIX_CACHE_ENTRIES
				&& result.length <= MAX_PREFIX_CACHE_ORDINALS - cachedPrefixOrdinals
				&& !prefixCache.containsKey(key)) {
				prefixCache.put(key, result);
				cachedPrefixOrdinals += result.length;
			}
		}
	}

	private NamespaceResult cachedNamespace(NamespaceCacheKey key) {
		if (!cachesEnabled) {
			return null;
		}
		synchronized (cacheLock) {
			return cachesEnabled ? namespaceCache.get(key) : null;
		}
	}

	private void cacheNamespace(NamespaceCacheKey key, NamespaceResult result) {
		if (!cachesEnabled) {
			return;
		}
		synchronized (cacheLock) {
			if (cachesEnabled && namespaceCache.size() < MAX_NAMESPACE_CACHE_ENTRIES) {
				namespaceCache.putIfAbsent(key, result);
			}
		}
	}

	private static void stableSortOrdinals(
		int[] ordinals,
		int[] scratch,
		IndexedEntry[] entries,
		int start,
		int end
	) {
		if (end - start < 2) {
			return;
		}
		int middle = (start + end) >>> 1;
		stableSortOrdinals(ordinals, scratch, entries, start, middle);
		stableSortOrdinals(ordinals, scratch, entries, middle, end);

		int left = start;
		int right = middle;
		int output = start;
		while (left < middle && right < end) {
			String leftPath = entries[ordinals[left]].path();
			String rightPath = entries[ordinals[right]].path();
			if (leftPath.compareTo(rightPath) <= 0) {
				scratch[output++] = ordinals[left++];
			} else {
				scratch[output++] = ordinals[right++];
			}
		}
		while (left < middle) {
			scratch[output++] = ordinals[left++];
		}
		while (right < end) {
			scratch[output++] = ordinals[right++];
		}
		System.arraycopy(scratch, start, ordinals, start, end - start);
	}

	private static int initialEntryCapacity(int estimatedEntries) {
		if (estimatedEntries <= 0) {
			return 0;
		}
		return Math.min(estimatedEntries, MAX_PREALLOCATED_ENTRIES);
	}

	private static int initialMapCapacity(int estimatedEntries) {
		if (estimatedEntries <= 0) {
			return 1;
		}
		long requested = (long) estimatedEntries + (estimatedEntries >>> 1) + 1L;
		return (int) Math.min(Math.max(16L, requested), (long) MAX_PREALLOCATED_ENTRIES * 2L);
	}

	private static int grownCapacity(int current) {
		if (current >= Integer.MAX_VALUE - 8) {
			throw new IllegalStateException("ZIP contains too many central-directory entries to index");
		}
		int growth = Math.max(MIN_GROWTH, current >>> 1);
		if (current > Integer.MAX_VALUE - 8 - growth) {
			return Integer.MAX_VALUE - 8;
		}
		return current + growth;
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

	private enum PrefixMode {
		FILES_LEXICAL,
		ALL_LEXICAL,
		ALL_CENTRAL
	}

	private record PrefixRange(int startInclusive, int endExclusive) {}

	private record PrefixCacheKey(String prefix, PrefixMode mode) {}

	private record NamespaceCacheKey(String prefix, ResourceNamePolicy policy) {}
}
