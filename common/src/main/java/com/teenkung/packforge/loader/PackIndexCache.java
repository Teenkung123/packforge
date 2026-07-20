package com.teenkung.packforge.loader;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Adapter-facing facade for per-owner {@link PackArchiveState}. */
public final class PackIndexCache {
	public static PackIndex getOrBuild(
		PackArchiveState state,
		ZipFile zipFile,
		String archiveName,
		Consumer<PackArchiveState.IndexFailure> onFirstFailure
	) {
		return state.index(zipFile, archiveName, onFirstFailure);
	}

	public static void invalidate(PackArchiveState state) throws IOException {
		state.invalidate();
	}

	public static void close(PackArchiveState state) throws IOException {
		state.close();
	}

	private PackIndexCache() {}
}
