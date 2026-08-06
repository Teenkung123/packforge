package com.teenkung.packforge.neoforge;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.PackForgeCore;
import com.teenkung.packforge.client.PackForgeClient;
import com.teenkung.packforge.client.config.PackForgeConfigScreen;
import com.teenkung.packforge.client.RuntimeSmokeController;
import com.teenkung.packforge.platform.PackForgeServices;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(PackForge.MOD_ID)
public final class PackForgeNeoForge {
	public PackForgeNeoForge(ModContainer container) {
		PackForgeServices.init(new NeoForgePackForgePlatform());
		PackForgeCore.init();
		PackForgeClient.initClient();
		RuntimeSmokeController.init();
		IConfigScreenFactory configScreenFactory =
			(ignored, parent) -> new PackForgeConfigScreen(parent);
		container.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
	}
}
