package com.teenkung.packforge.loader;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Standalone, non-gating microbenchmark used by the Gradle benchmark task. */
public final class PackIndexBenchmark {
	private static volatile long blackhole;

	public static void main(String[] arguments) throws Exception {
		Path outputDirectory = arguments.length == 0
			? Path.of("build", "packforge-benchmark")
			: Path.of(arguments[0]);
		Files.createDirectories(outputDirectory);
		Path archive = outputDirectory.resolve("deterministic-large-pack.zip");
		String packMetadata = arguments.length < 2
			? DeterministicZipFixture.defaultPackMetadata()
			: new String(Base64.getDecoder().decode(arguments[1]), StandardCharsets.UTF_8);
		DeterministicZipFixture.createRuntimeCompatible(archive, 20_000, packMetadata);

		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			long buildStarted = System.nanoTime();
			PackIndex index = PackIndex.build(zipFile);
			long buildNanos = System.nanoTime() - buildStarted;
			String namespacePrefix = "assets/";
			String resourcePrefix = "assets/generated3/textures/generated/";

			for (int i = 0; i < 5; i++) {
				baselineScan(zipFile, resourcePrefix, namespacePrefix, 20);
				indexedScan(index, resourcePrefix, namespacePrefix, 20);
			}

			long[] baseline = new long[5];
			long[] indexed = new long[5];
			for (int i = 0; i < baseline.length; i++) {
				baseline[i] = baselineScan(zipFile, resourcePrefix, namespacePrefix, 100);
				indexed[i] = indexedScan(index, resourcePrefix, namespacePrefix, 100);
			}
			Arrays.sort(baseline);
			Arrays.sort(indexed);
			long baselineMedian = baseline[baseline.length / 2];
			long indexedMedian = indexed[indexed.length / 2];
			String baselineHash = resolvedResourceHash(zipFile, null);
			String indexedHash = resolvedResourceHash(zipFile, index);
			if (!baselineHash.equals(indexedHash)) {
				throw new IllegalStateException("Indexed resource resolution changed the deterministic fixture hash");
			}
			double improvement = baselineMedian == 0L
				? 0.0D
				: 100.0D * (baselineMedian - indexedMedian) / baselineMedian;

			System.out.printf(
				"{\"representation\":\"IndexedEntry[]+int[]\",\"centralEntries\":%d,\"uniquePaths\":%d,\"duplicatePaths\":%d,\"indexBuildNs\":%d,\"baselineMedianNs\":%d,\"indexedMedianNs\":%d,\"improvementPercent\":%.2f,\"prefixCacheEntries\":%d,\"prefixCacheLimit\":%d,\"cachedPrefixOrdinals\":%d,\"prefixOrdinalLimit\":%d,\"namespaceCacheEntries\":%d,\"namespaceCacheLimit\":%d,\"cachesEnabled\":%s,\"baselineHash\":\"%s\",\"indexedHash\":\"%s\",\"checksum\":%d}%n",
				index.entryCount(), index.size(), index.duplicatePathCount(), buildNanos,
				baselineMedian, indexedMedian, improvement,
				index.prefixCacheSize(), PackIndex.MAX_PREFIX_CACHE_ENTRIES,
				index.cachedPrefixOrdinalCount(), PackIndex.MAX_PREFIX_CACHE_ORDINALS,
				index.namespaceCacheSize(), PackIndex.MAX_NAMESPACE_CACHE_ENTRIES,
				index.cachesEnabled(), baselineHash, indexedHash, blackhole
			);
		}
	}

	private static String resolvedResourceHash(ZipFile zipFile, PackIndex index) throws Exception {
		TreeSet<String> paths = new TreeSet<>();
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (!entry.isDirectory()) {
				paths.add(entry.getName());
			}
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (String path : paths) {
			ZipEntry entry = index == null ? zipFile.getEntry(path) : index.entryFor(path);
			digest.update(path.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			try (var input = zipFile.getInputStream(entry)) {
				input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static long baselineScan(ZipFile zipFile, String resourcePrefix, String namespacePrefix, int repetitions) {
		long start = System.nanoTime();
		long count = 0L;
		for (int repetition = 0; repetition < repetitions; repetition++) {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				String path = entries.nextElement().getName();
				if (path.startsWith(resourcePrefix)) {
					count++;
				}
				if (path.startsWith(namespacePrefix)) {
					count += path.length() & 1;
				}
			}
		}
		blackhole = count;
		return System.nanoTime() - start;
	}

	private static long indexedScan(PackIndex index, String resourcePrefix, String namespacePrefix, int repetitions) {
		long start = System.nanoTime();
		long[] count = { 0L };
		for (int repetition = 0; repetition < repetitions; repetition++) {
			index.forEachFileWithPrefix(resourcePrefix, entry -> count[0]++);
			count[0] += index.namespacesFor(namespacePrefix, ResourceNamePolicy.current()).valid().size();
		}
		blackhole = count[0];
		return System.nanoTime() - start;
	}

	private PackIndexBenchmark() {}
}
