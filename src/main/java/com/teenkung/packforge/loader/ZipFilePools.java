package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.mixin.loader.SharedZipFileAccessAccessor;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipFilePools {
	private static final Map<Object, Pool> POOLS = new ConcurrentHashMap<>();

	public static IoSupplier<InputStream> supplier(Object access, String path, ZipFile fallbackZip, ZipEntry fallbackEntry) {
		if (!FeatureFlags.loaderZipPoolEnabled()) {
			return IoSupplier.create(fallbackZip, fallbackEntry);
		}
		Pool pool = POOLS.computeIfAbsent(access, ZipFilePools::createPool);
		if (pool == null) {
			return IoSupplier.create(fallbackZip, fallbackEntry);
		}
		return () -> {
			ZipFile zip = pool.current();
			ZipEntry entry = zip.getEntry(path);
			if (entry == null) {
				throw new IOException("Missing pooled ZIP entry: " + path);
			}
			return zip.getInputStream(entry);
		};
	}

	public static void close(Object access) {
		Pool pool = POOLS.remove(access);
		if (pool != null) {
			pool.close();
		}
	}

	private static Pool createPool(Object access) {
		try {
			return new Pool(((SharedZipFileAccessAccessor) access).packforge$file());
		} catch (Throwable t) {
			PackForge.LOGGER.warn("PackForge ZIP read pool unavailable; falling back to vanilla ZipFile", t);
			return null;
		}
	}

	private static final class Pool {
		private final java.io.File file;
		private final ConcurrentHashMap<Long, ZipFile> handles = new ConcurrentHashMap<>();

		private Pool(java.io.File file) {
			this.file = file;
		}

		private ZipFile current() throws IOException {
			long threadId = Thread.currentThread().threadId();
			ZipFile existing = handles.get(threadId);
			if (existing != null) {
				return existing;
			}
			ZipFile created = new ZipFile(file);
			ZipFile raced = handles.putIfAbsent(threadId, created);
			if (raced != null) {
				created.close();
				return raced;
			}
			return created;
		}

		private void close() {
			for (ZipFile zip : handles.values()) {
				try {
					zip.close();
				} catch (IOException ignored) {
				}
			}
			handles.clear();
		}
	}

	private ZipFilePools() {}
}
