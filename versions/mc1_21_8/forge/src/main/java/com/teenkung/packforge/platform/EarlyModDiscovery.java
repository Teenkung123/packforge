package com.teenkung.packforge.platform;

import net.minecraftforge.fml.loading.LoadingModList;

/** Forge's discovery list is populated before mod construction and transformation. */
public final class EarlyModDiscovery {
	public static OptimizationCompatibility.State capture() {
		LoadingModList modList = LoadingModList.get();
		return OptimizationCompatibility.installEarly("forge", modId -> modList.getModFileById(modId) != null);
	}
	private EarlyModDiscovery() {}
}
