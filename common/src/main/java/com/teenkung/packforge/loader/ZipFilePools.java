package com.teenkung.packforge.loader;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/** Adapter-facing facade for bounded per-owner ZIP read pools. */
public final class ZipFilePools {
	public static final int DEFAULT_MAX_HANDLES = ZipReadPool.DEFAULT_MAX_HANDLES;

	public static InputStreamSupplier supplier(
		PackArchiveState state,
		File archiveFile,
		String path,
		InputStreamSupplier fallback,
		Consumer<ZipReadPool.Failure> onFirstFailure
	) {
		return state.pooledSupplier(
			archiveFile,
			DEFAULT_MAX_HANDLES,
			path,
			fallback,
			onFirstFailure
		);
	}

	public static void invalidate(PackArchiveState state) throws IOException {
		state.invalidate();
	}

	public static void close(PackArchiveState state) throws IOException {
		state.close();
	}

	private ZipFilePools() {}
}
