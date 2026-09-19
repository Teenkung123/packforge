package com.teenkung.packforge.platform;

import net.neoforged.fml.loading.FMLLoader;

/** FancyModLoader finishes discovery before it initializes mod mixin configurations. */
public final class EarlyModDiscovery {
	public static OptimizationCompatibility.State capture() {
		var modList = FMLLoader.getCurrent().getLoadingModList();
		return OptimizationCompatibility.installEarly("neoforge", modId -> modList.getModFileById(modId) != null);
	}

	private EarlyModDiscovery() {}
}
