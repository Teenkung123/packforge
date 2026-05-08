package com.teenkung.packforge.fabric;

import com.teenkung.packforge.PackForgeCore;
import com.teenkung.packforge.platform.PackForgeServices;
import net.fabricmc.api.ModInitializer;

public final class PackForgeFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		PackForgeServices.init(new FabricPackForgePlatform());
		PackForgeCore.init();
	}
}
