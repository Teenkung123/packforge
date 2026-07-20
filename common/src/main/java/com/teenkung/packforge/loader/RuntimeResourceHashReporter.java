package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgeServices;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.DigestOutputStream;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Test-only resolved-resource hashing, enabled explicitly by the runtime harness. */
public final class RuntimeResourceHashReporter {
	private static final String ENABLE_ENV = "PACKFORGE_RUNTIME_RESOURCE_HASH";

	public static void reportAsync(long reloadId, ResourceSnapshotSupplier snapshotSupplier) {
		if (!enabled()) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			try {
				Snapshot snapshot = hash(snapshotSupplier.get());
				PackForge.LOGGER.info("PackForge resolved-resource hash: id={} entries={} sha256={}",
					reloadId, snapshot.entries(), snapshot.sha256());
			} catch (Exception exception) {
				PackForge.LOGGER.error("PackForge failed to hash resolved fixture resources for reload {}", reloadId, exception);
			}
		}, backgroundExecutor());
	}

	static Snapshot hash(Map<String, InputStreamSupplier> resources) throws Exception {
		TreeMap<String, InputStreamSupplier> sorted = new TreeMap<>(resources);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (Map.Entry<String, InputStreamSupplier> entry : sorted.entrySet()) {
			digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			try (InputStream input = entry.getValue().get()) {
				input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
			}
			digest.update((byte) 0xff);
		}
		return new Snapshot(sorted.size(), HexFormat.of().formatHex(digest.digest()));
	}

	private static boolean enabled() {
		return Boolean.parseBoolean(System.getenv().getOrDefault(ENABLE_ENV, "false"));
	}

	private static Executor backgroundExecutor() {
		return PackForgeServices.isInitialized() ? PackForgeServices.platform().backgroundExecutor() : ForkJoinPool.commonPool();
	}

	@FunctionalInterface
	public interface ResourceSnapshotSupplier {
		Map<String, InputStreamSupplier> get() throws Exception;
	}

	record Snapshot(int entries, String sha256) {}

	private RuntimeResourceHashReporter() {}
}
