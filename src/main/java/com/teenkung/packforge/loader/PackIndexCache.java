package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import net.minecraft.server.packs.FilePackResources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

public final class PackIndexCache {
	private static final Map<FilePackResources.SharedZipFileAccess, PackIndex> CACHE = new ConcurrentHashMap<>();

	public static PackIndex getOrBuild(FilePackResources.SharedZipFileAccess access) {
		ZipFile zf = access.getOrCreateZipFile();
		if (zf == null) return null;
		PackIndex existing = CACHE.get(access);
		if (existing != null && existing.zipFile() == zf) return existing;

		synchronized (access) {
			zf = access.getOrCreateZipFile();
			if (zf == null) return null;
			existing = CACHE.get(access);
			if (existing != null && existing.zipFile() == zf) return existing;

			PackIndex built;
			try {
				built = PackIndex.build(zf);
			} catch (Throwable t) {
				PackForge.LOGGER.warn("PackIndex build failed; falling back to vanilla", t);
				return null;
			}
			CACHE.put(access, built);
			return built;
		}
	}

	public static void invalidate(FilePackResources.SharedZipFileAccess access) {
		CACHE.remove(access);
	}

	private PackIndexCache() {}
}
