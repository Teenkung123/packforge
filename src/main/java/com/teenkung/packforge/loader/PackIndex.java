package com.teenkung.packforge.loader;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackIndex {
	private final ZipFile zipFile;
	private final String[] sortedPaths;
	private final ZipEntry[] sortedEntries;
	private final Map<String, ZipEntry> exactLookup;
	private final Map<String, Set<String>> namespaceCache = new ConcurrentHashMap<>();

	private PackIndex(ZipFile zipFile, String[] sortedPaths, ZipEntry[] sortedEntries, Map<String, ZipEntry> exactLookup) {
		this.zipFile = zipFile;
		this.sortedPaths = sortedPaths;
		this.sortedEntries = sortedEntries;
		this.exactLookup = exactLookup;
	}

	public static PackIndex build(ZipFile zipFile) {
		Enumeration<? extends ZipEntry> e = zipFile.entries();
		Map<String, ZipEntry> exact = new HashMap<>();
		while (e.hasMoreElements()) {
			ZipEntry ze = e.nextElement();
			if (ze.isDirectory()) continue;
			exact.put(ze.getName(), ze);
		}
		String[] paths = exact.keySet().toArray(new String[0]);
		Arrays.sort(paths);
		ZipEntry[] entries = new ZipEntry[paths.length];
		for (int i = 0; i < paths.length; i++) entries[i] = exact.get(paths[i]);
		return new PackIndex(zipFile, paths, entries, exact);
	}

	public ZipFile zipFile() { return zipFile; }

	public ZipEntry entryFor(String fullPath) {
		return exactLookup.get(fullPath);
	}

	public void forEachWithPrefix(String prefix, BiConsumer<String, ZipEntry> out) {
		int idx = lowerBound(prefix);
		for (int i = idx; i < sortedPaths.length; i++) {
			String p = sortedPaths[i];
			if (!p.startsWith(prefix)) break;
			out.accept(p, sortedEntries[i]);
		}
	}

	public Set<String> namespacesFor(String typePrefix) {
		Set<String> cached = namespaceCache.get(typePrefix);
		if (cached != null) return cached;
		int idx = lowerBound(typePrefix);
		HashSet<String> ns = new HashSet<>();
		for (int i = idx; i < sortedPaths.length; i++) {
			String p = sortedPaths[i];
			if (!p.startsWith(typePrefix)) break;
			int slash = p.indexOf('/', typePrefix.length());
			String candidate = (slash == -1) ? p.substring(typePrefix.length()) : p.substring(typePrefix.length(), slash);
			if (candidate.isEmpty()) continue;
			if (Identifier.isValidNamespace(candidate)) ns.add(candidate);
		}
		Set<String> out = Collections.unmodifiableSet(ns);
		namespaceCache.put(typePrefix, out);
		return out;
	}

	private int lowerBound(String key) {
		int lo = 0, hi = sortedPaths.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (sortedPaths[mid].compareTo(key) < 0) lo = mid + 1; else hi = mid;
		}
		return lo;
	}

	public int size() { return sortedPaths.length; }

	@SuppressWarnings("unused")
	public static String fullPath(PackType type, Identifier id) {
		return type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath();
	}
}
