package com.teenkung.packforge.platform;

import net.fabricmc.loader.api.FabricLoader;

/** Fabric's resolved mod list is available before mixin configuration plugins run. */
public final class EarlyModDiscovery {
	public static OptimizationCompatibility.State capture() {
		return OptimizationCompatibility.installEarly("fabric", FabricLoader.getInstance()::isModLoaded);
	}
	private EarlyModDiscovery() {}
}
