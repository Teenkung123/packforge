package com.teenkung.packforge.loader;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact-version bridge used only by the runtime equivalence harness. */
public final class RuntimeResourceHash {
	public static void report(ReloadableResourceManager manager, long reloadId) {
		RuntimeResourceHashReporter.reportAsync(reloadId, () -> snapshot(manager));
	}

	private static Map<String, InputStreamSupplier> snapshot(ReloadableResourceManager manager) {
		Map<String, InputStreamSupplier> snapshot = new LinkedHashMap<>();
		Map<Identifier, Resource> resources = manager.listResources("textures", RuntimeResourceHash::isFixtureResource);
		resources.forEach((location, resource) -> snapshot.put(location.toString(), resource::open));
		return snapshot;
	}

	private static boolean isFixtureResource(Identifier location) {
		String namespace = location.getNamespace();
		return namespace.equals("example") || namespace.startsWith("generated");
	}

	private RuntimeResourceHash() {}
}
