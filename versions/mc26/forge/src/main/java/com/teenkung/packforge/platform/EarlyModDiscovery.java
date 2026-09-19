package com.teenkung.packforge.platform;

import net.minecraftforge.fml.loading.LoadingModList;

/** Forge's discovery list is populated before mod construction and transformation. */
public final class EarlyModDiscovery {
	public static OptimizationCompatibility.State capture() {
		return OptimizationCompatibility.installEarly("forge", modId -> LoadingModList.getModFileById(modId) != null);
	}

	private EarlyModDiscovery() {}
}
