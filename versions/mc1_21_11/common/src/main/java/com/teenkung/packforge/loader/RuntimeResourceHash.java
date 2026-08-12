package com.teenkung.packforge.loader;

import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Exact-version bridge used only by the runtime equivalence harness. */
public final class RuntimeResourceHash {
	public static void report(ReloadableResourceManager manager, long reloadId) {
		RuntimeResourceHashReporter.reportAsync(reloadId, () -> snapshot(manager));
	}

	private static Map<String, InputStreamSupplier> snapshot(ReloadableResourceManager manager) {
		Map<String, InputStreamSupplier> snapshot = new LinkedHashMap<>();
		@SuppressWarnings({"rawtypes", "unchecked"})
		Predicate fixtureResource = location -> isFixtureResource(String.valueOf(location));
		Map<?, Resource> resources = manager.listResources("textures", fixtureResource);
		resources.forEach((location, resource) -> snapshot.put(location.toString(), resource::open));
		return snapshot;
	}

	private static boolean isFixtureResource(String location) {
		int separator = location.indexOf(':');
		String namespace = separator < 0 ? "minecraft" : location.substring(0, separator);
		return namespace.equals("example") || namespace.startsWith("generated");
	}

	private RuntimeResourceHash() {}
}
