package com.teenkung.packforge.loader;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/**
 * Per-archive-owner state intended for an adapter {@code @Unique} field.
 * Modern versions attach one instance to SharedZipFileAccess; Minecraft 1.20.1
 * attaches it directly to FilePackResources.
 */
public final class PackArchiveState implements AutoCloseable {
	private final Object lock = new Object();
	private final IndexBuilder indexBuilder;
	private volatile IndexSnapshot indexSnapshot = IndexSnapshot.uninitialized();
	private ZipReadPool readPool;
	private boolean closed;

	public PackArchiveState() {
		this(PackIndex::build);
	}

	PackArchiveState(IndexBuilder indexBuilder) {
		this.indexBuilder = Objects.requireNonNull(indexBuilder, "indexBuilder");
	}

	/**
	 * Builds at most once for a particular ZipFile identity. A RuntimeException
	 * is cached and reported once; Error is deliberately allowed to propagate.
	 */
	public PackIndex index(
		ZipFile zipFile,
		String archiveName,
		Consumer<IndexFailure> onFirstFailure
	) {
		Objects.requireNonNull(zipFile, "zipFile");
		Objects.requireNonNull(archiveName, "archiveName");
		Objects.requireNonNull(onFirstFailure, "onFirstFailure");

		IndexSnapshot snapshot = indexSnapshot;
		if (snapshot.zipFile == zipFile) {
			return snapshot.index;
		}

		IndexFailure report = null;
		PackIndex result;
		PackIndex staleIndex = null;
		synchronized (lock) {
			if (closed) {
				return null;
			}
			snapshot = indexSnapshot;
			if (snapshot.zipFile == zipFile) {
				return snapshot.index;
			}
			staleIndex = snapshot.index;
			try {
				result = indexBuilder.build(zipFile);
				indexSnapshot = IndexSnapshot.ready(zipFile, result);
			} catch (RuntimeException exception) {
				result = null;
				indexSnapshot = IndexSnapshot.failed(zipFile, exception);
				report = new IndexFailure(archiveName, exception);
			}
		}

		if (staleIndex != null) {
			staleIndex.invalidateCaches();
		}
		if (report != null) {
			onFirstFailure.accept(report);
		}
		return result;
	}

	/**
	 * Returns a supplier bound to the current pool generation. Once invalidated
	 * or closed, an already-returned supplier can only use its vanilla fallback;
	 * it cannot recreate pooled handles.
	 */
	public InputStreamSupplier pooledSupplier(
		File archiveFile,
		int maxHandles,
		String path,
		InputStreamSupplier fallback,
		Consumer<ZipReadPool.Failure> onFirstFailure
	) {
		Objects.requireNonNull(archiveFile, "archiveFile");
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(fallback, "fallback");
		Objects.requireNonNull(onFirstFailure, "onFirstFailure");

		ZipReadPool pool;
		synchronized (lock) {
			if (closed) {
				return fallback;
			}
			if (readPool == null) {
				readPool = new ZipReadPool(archiveFile, maxHandles);
			} else if (!readPool.archiveFile().equals(archiveFile)) {
				return fallback;
			}
			pool = readPool;
		}
		return () -> pool.open(path, fallback, onFirstFailure);
	}

	public IndexStatus status() {
		return indexSnapshot.status;
	}

	public ZipFile indexedZipFile() {
		return indexSnapshot.zipFile;
	}

	public RuntimeException indexFailure() {
		return indexSnapshot.failure;
	}

	public boolean isClosed() {
		synchronized (lock) {
			return closed;
		}
	}

	/** Clears cached failure/success and closes pooled handles, allowing reuse. */
	public void invalidate() throws IOException {
		ZipReadPool pool;
		PackIndex index;
		synchronized (lock) {
			if (closed) {
				return;
			}
			index = indexSnapshot.index;
			indexSnapshot = IndexSnapshot.uninitialized();
			pool = readPool;
			readPool = null;
		}
		if (index != null) {
			index.invalidateCaches();
		}
		if (pool != null) {
			pool.close();
		}
	}

	/** Permanently closes this archive owner. This operation is idempotent. */
	@Override
	public void close() throws IOException {
		ZipReadPool pool;
		PackIndex index;
		synchronized (lock) {
			if (closed) {
				return;
			}
			closed = true;
			index = indexSnapshot.index;
			indexSnapshot = IndexSnapshot.uninitialized();
			pool = readPool;
			readPool = null;
		}
		if (index != null) {
			index.invalidateCaches();
		}
		if (pool != null) {
			pool.close();
		}
	}

	public enum IndexStatus {
		UNINITIALIZED,
		READY,
		FAILED
	}

	public record IndexFailure(String archiveName, RuntimeException cause) {
		public IndexFailure {
			Objects.requireNonNull(archiveName, "archiveName");
			Objects.requireNonNull(cause, "cause");
		}
	}

	@FunctionalInterface
	interface IndexBuilder {
		PackIndex build(ZipFile zipFile);
	}

	private static final class IndexSnapshot {
		private final IndexStatus status;
		private final ZipFile zipFile;
		private final PackIndex index;
		private final RuntimeException failure;

		private IndexSnapshot(IndexStatus status, ZipFile zipFile, PackIndex index, RuntimeException failure) {
			this.status = status;
			this.zipFile = zipFile;
			this.index = index;
			this.failure = failure;
		}

		private static IndexSnapshot uninitialized() {
			return new IndexSnapshot(IndexStatus.UNINITIALIZED, null, null, null);
		}

		private static IndexSnapshot ready(ZipFile zipFile, PackIndex index) {
			return new IndexSnapshot(IndexStatus.READY, zipFile, index, null);
		}

		private static IndexSnapshot failed(ZipFile zipFile, RuntimeException failure) {
			return new IndexSnapshot(IndexStatus.FAILED, zipFile, null, failure);
		}
	}
}
