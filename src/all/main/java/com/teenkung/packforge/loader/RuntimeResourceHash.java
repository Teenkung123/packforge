package com.teenkung.packforge.loader;

//? if resource_selector_compat {
/*
import com.teenkung.packforge.internal.loader.ResourceManagerSelectorBridge;
*///?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

//? if resource_selector_compat {
/*
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
*///?}
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact-version bridge used only by the runtime equivalence harness. */
public final class RuntimeResourceHash {
	public static void report(ReloadableResourceManager manager, long reloadId) {
		RuntimeResourceHashReporter.reportAsync(reloadId, () -> snapshot(manager));
	}

	private static Map<String, InputStreamSupplier> snapshot(ReloadableResourceManager manager) {
		Map<String, InputStreamSupplier> snapshot = new LinkedHashMap<>();
		Map<ResourceLocation, Resource> resources = listResources(manager);
		resources.forEach((location, resource) -> snapshot.put(location.toString(), resource::open));
		return snapshot;
	}

	@SuppressWarnings("unchecked")
	private static Map<ResourceLocation, Resource> listResources(ReloadableResourceManager manager) {
//? if resource_selector_compat {
		ResourceManagerSelectorBridge selectorBridge = SelectorBridgeHolder.INSTANCE;
		if (selectorBridge == null) {
			return manager.listResources("textures", RuntimeResourceHash::isFixtureResource);
		}
		return (Map<ResourceLocation, Resource>) selectorBridge.list(manager, "textures");
//?} else {
		/*
		return manager.listResources("textures", RuntimeResourceHash::isFixtureResource);
		*///?}
	}

//? if resource_selector_compat {
	/*
	private static ResourceManagerSelectorBridge createSelectorBridge() {
		try {
			MethodHandle predicate = MethodHandles.lookup().findStatic(
				RuntimeResourceHash.class,
				"isFixtureResource",
				MethodType.methodType(boolean.class, ResourceLocation.class)
			);
			return ResourceManagerSelectorBridge.discover(
				ReloadableResourceManager.class,
				"net.minecraft.server.packs.resources.ResourceManager$Selector",
				predicate
			);
		} catch (NoSuchMethodException | IllegalAccessException exception) {
			throw new IllegalStateException("Could not bind the runtime resource hash predicate", exception);
		}
	}

	private static final class SelectorBridgeHolder {
		private static final ResourceManagerSelectorBridge INSTANCE = createSelectorBridge();
	}
*///?}

	private static boolean isFixtureResource(ResourceLocation location) {
		String namespace = location.getNamespace();
		return namespace.equals("example") || namespace.startsWith("generated");
	}

	private RuntimeResourceHash() {}
}
