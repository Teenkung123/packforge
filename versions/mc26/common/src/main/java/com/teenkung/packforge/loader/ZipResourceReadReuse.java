package com.teenkung.packforge.loader;

import com.teenkung.packforge.concurrent.PreparationBudget;

import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reuses ZIP reads only when an already-built PackIndex proves the exact path unique.
 * ZipEntry identity alone is insufficient: Java's duplicate-name stream selection also
 * depends on the ZIP handle's most recent lookup/enumeration. No new ZIP scans occur here.
 * Certificates belong to one reload, not to persistent vanilla resource suppliers.
 */
public final class ZipResourceReadReuse {
	private static final Object REGISTRY_LOCK = new Object();
	private static final long REGISTRY_BYTES = 512;
	private static final long CERTIFICATE_BYTES = 512;
	private static Certificates registries;

	private ZipResourceReadReuse() {}

	/** Called only after normal FilePack processing has already resolved an index. */
	public static void register(ReloadExecutionContext context, PackIndex index) {
		if (context == null || !context.features().resourceReadReuseEnabled() || index == null
			|| index.zipFile().getClass() != ZipFile.class || !index.cachesEnabled()) return;
		PreparationBudget.Scope scope = context.preparationBudget();
		Certificates created = null;
		synchronized (REGISTRY_LOCK) {
			if (scope.isRetired()) return;
			Certificates registry = find(scope);
			if (registry == null) {
				PreparationBudget.Reservation memory = scope.tryReserve(REGISTRY_BYTES);
				if (memory == null) return;
				try { registry = new Certificates(scope, memory); }
				catch (RuntimeException | Error failure) { memory.close(); throw failure; }
				registry.next = registries;
				registries = registry;
				created = registry;
			}
			Certificate existing = registry.entries.get(index.zipFile());
			if (existing != null) existing.index = index;
			else {
				PreparationBudget.Reservation memory = scope.tryReserve(CERTIFICATE_BYTES);
				if (memory != null) {
					try { registry.entries.put(index.zipFile(), new Certificate(index, memory)); }
					catch (RuntimeException | Error failure) {
						memory.close();
						if (created != null) retire(created);
						throw failure;
					}
				}
			}
		}
		// Never invoke retirement callbacks while holding the registry lock.
		if (created != null) {
			Certificates registered = created;
			try { scope.onRetire(() -> retire(registered)); }
			catch (RuntimeException | Error failure) { retire(registered); throw failure; }
		}
	}

	public static InputStream open(ReloadExecutionContext context, ZipFile zipFile, ZipEntry entry,
		ReloadReadCache.StreamSource original) throws IOException {
		if (!eligible(context, zipFile, entry)) return original.open();
		ReloadReadCache cache = context.readCache();
		if (cache == null) return original.open();
		// Do not let a closed archive become readable solely because its bytes were cached.
		zipFile.size();
		return cache.open(zipFile, entry.getName(), entry.getSize(), true, original);
	}

	private static boolean eligible(ReloadExecutionContext context, ZipFile zipFile, ZipEntry entry) {
		if (context == null || !context.features().resourceReadReuseEnabled() || zipFile == null || entry == null
			|| zipFile.getClass() != ZipFile.class || entry.getClass() != ZipEntry.class
			|| entry.isDirectory() || entry.getSize() < 0 || entry.getSize() > ReloadReadCache.MAX_PAYLOAD) return false;
		String name = entry.getName();
		if (!name.endsWith(".json") && !name.endsWith(".mcmeta")) return false;
		PreparationBudget.Scope scope = context.preparationBudget();
		synchronized (REGISTRY_LOCK) {
			if (scope.isRetired()) return false;
			Certificates registry = find(scope);
			Certificate certificate = registry == null ? null : registry.entries.get(zipFile);
			if (certificate == null) return false;
			PackIndex index = certificate.index;
			return index.cachesEnabled() && !index.hasDuplicatePath(name) && index.entryFor(name) != null;
		}
	}

	private static Certificates find(PreparationBudget.Scope scope) {
		for (Certificates current = registries; current != null; current = current.next) {
			if (current.scope == scope) return current;
		}
		return null;
	}

	private static void retire(Certificates registry) {
		synchronized (REGISTRY_LOCK) {
			Certificates previous = null;
			Certificates current = registries;
			while (current != null && current != registry) { previous = current; current = current.next; }
			if (current == null) return;
			if (previous == null) registries = current.next;
			else previous.next = current.next;
			for (Certificate certificate : registry.entries.values()) certificate.memory.close();
			registry.entries = Map.of();
			registry.next = null;
			registry.memory.close();
		}
	}

	/** Linked registry roots avoid retaining an oversized global hash table after scope retirement. */
	private static final class Certificates {
		final PreparationBudget.Scope scope;
		final PreparationBudget.Reservation memory;
		Map<ZipFile, Certificate> entries = new IdentityHashMap<>(2);
		Certificates next;
		Certificates(PreparationBudget.Scope scope, PreparationBudget.Reservation memory) {
			this.scope = scope;
			this.memory = memory;
		}
	}

	private static final class Certificate {
		PackIndex index;
		final PreparationBudget.Reservation memory;
		Certificate(PackIndex index, PreparationBudget.Reservation memory) {
			this.index = index;
			this.memory = memory;
		}
	}
}
