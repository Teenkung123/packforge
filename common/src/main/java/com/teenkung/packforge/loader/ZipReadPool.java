package com.teenkung.packforge.loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A small, bounded set of additional ZIP handles for parallel reads.
 *
 * <p>Handle creation and closure use the same monitor. This prevents a handle
 * from being registered after close and leaking a file lock. Any pool-specific
 * failure permanently falls back to the supplied vanilla reader.</p>
 */
public final class ZipReadPool implements AutoCloseable {
	public static final int DEFAULT_MAX_HANDLES = 8;
	public static final int MAX_HANDLES_LIMIT = 32;

	private final Object lock = new Object();
	private final File archiveFile;
	private final ZipFile[] handles;
	private final FileStamp expectedStamp;
	private Exception initializationFailure;
	private Exception failure;
	private boolean failureReported;
	private boolean closed;

	public ZipReadPool(File archiveFile, int maxHandles) {
		this.archiveFile = Objects.requireNonNull(archiveFile, "archiveFile");
		int boundedHandles = Math.max(1, Math.min(MAX_HANDLES_LIMIT, maxHandles));
		this.handles = new ZipFile[boundedHandles];
		FileStamp stamp = null;
		try {
			stamp = FileStamp.read(archiveFile);
		} catch (IOException | RuntimeException exception) {
			this.initializationFailure = exception;
		}
		this.expectedStamp = stamp;
	}

	public File archiveFile() {
		return archiveFile;
	}

	public int maxHandles() {
		return handles.length;
	}

	public boolean isClosed() {
		synchronized (lock) {
			return closed;
		}
	}

	public boolean isFailed() {
		synchronized (lock) {
			return failure != null || initializationFailure != null;
		}
	}

	/** Visible for diagnostics and focused lifecycle tests. */
	public int openHandleCount() {
		synchronized (lock) {
			int count = 0;
			for (ZipFile handle : handles) {
				if (handle != null) {
					count++;
				}
			}
			return count;
		}
	}

	public InputStream open(
		String path,
		InputStreamSupplier fallback,
		Consumer<Failure> onFirstFailure
	) throws IOException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(fallback, "fallback");
		Objects.requireNonNull(onFirstFailure, "onFirstFailure");

		ZipFile handle;
		Failure report = null;
		synchronized (lock) {
			if (closed || failure != null) {
				handle = null;
			} else if (initializationFailure != null) {
				report = failLocked(initializationFailure);
				initializationFailure = null;
				handle = null;
			} else {
				int slot = Math.floorMod(System.identityHashCode(Thread.currentThread()), handles.length);
				handle = handles[slot];
				if (handle == null) {
					try {
						FileStamp currentStamp = FileStamp.read(archiveFile);
						if (!expectedStamp.equals(currentStamp)) {
							throw new IOException("ZIP resource pack changed while it was open: " + archiveFile);
						}
						handle = new ZipFile(archiveFile);
						handles[slot] = handle;
					} catch (IOException | RuntimeException exception) {
						report = failLocked(exception);
						handle = null;
					}
				}
			}
		}

		if (report != null) {
			onFirstFailure.accept(report);
		}
		if (handle == null) {
			return fallback.get();
		}

		try {
			ZipEntry entry = handle.getEntry(path);
			if (entry == null) {
				IOException exception = new IOException("Missing pooled ZIP entry: " + path);
				reportFailure(exception, onFirstFailure);
				return fallback.get();
			}
			return handle.getInputStream(entry);
		} catch (IOException | RuntimeException exception) {
			reportFailure(exception, onFirstFailure);
			return fallback.get();
		}
	}

	private void reportFailure(Exception exception, Consumer<Failure> onFirstFailure) {
		Failure report;
		synchronized (lock) {
			report = failLocked(exception);
		}
		if (report != null) {
			onFirstFailure.accept(report);
		}
	}

	private Failure failLocked(Exception exception) {
		if (failure != null) {
			return null;
		}
		failure = exception;
		IOException closeFailure = closeHandlesLocked();
		if (closeFailure != null) {
			exception.addSuppressed(closeFailure);
		}
		if (failureReported) {
			return null;
		}
		failureReported = true;
		return new Failure(archiveFile, exception);
	}

	@Override
	public void close() throws IOException {
		IOException closeFailure;
		synchronized (lock) {
			if (closed) {
				return;
			}
			closed = true;
			closeFailure = closeHandlesLocked();
		}
		if (closeFailure != null) {
			throw closeFailure;
		}
	}

	private IOException closeHandlesLocked() {
		IOException firstFailure = null;
		for (int i = 0; i < handles.length; i++) {
			ZipFile handle = handles[i];
			handles[i] = null;
			if (handle == null) {
				continue;
			}
			try {
				handle.close();
			} catch (IOException exception) {
				if (firstFailure == null) {
					firstFailure = exception;
				} else {
					firstFailure.addSuppressed(exception);
				}
			}
		}
		return firstFailure;
	}

	public record Failure(File archiveFile, Exception cause) {
		public Failure {
			Objects.requireNonNull(archiveFile, "archiveFile");
			Objects.requireNonNull(cause, "cause");
		}
	}

	private record FileStamp(long size, long lastModifiedMillis) {
		static FileStamp read(File file) throws IOException {
			BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
			return new FileStamp(attributes.size(), attributes.lastModifiedTime().toMillis());
		}
	}
}
