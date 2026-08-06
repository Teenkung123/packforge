package com.teenkung.packforge.forge;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.PackForgeCore;
import com.teenkung.packforge.client.PackForgeClient;
import com.teenkung.packforge.client.config.PackForgeConfigScreen;
import com.teenkung.packforge.client.RuntimeSmokeController;
import com.teenkung.packforge.platform.PackForgeServices;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(PackForge.MOD_ID)
public final class PackForgeForge {
	public PackForgeForge() {
		PackForgeServices.init(new ForgePackForgePlatform());
		PackForgeCore.init();
		PackForgeClient.initClient();
		RuntimeSmokeController.init();
		ModLoadingContext.get().registerExtensionPoint(
			ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory(
				(ignored, parent) -> new PackForgeConfigScreen(parent)
			)
		);
	}
}
