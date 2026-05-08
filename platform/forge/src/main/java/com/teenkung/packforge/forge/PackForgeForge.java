package com.teenkung.packforge.forge;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.PackForgeCore;
import com.teenkung.packforge.client.PackForgeClient;
import com.teenkung.packforge.platform.PackForgeServices;
import net.minecraftforge.fml.common.Mod;

@Mod(PackForge.MOD_ID)
public final class PackForgeForge {
	public PackForgeForge() {
		PackForgeServices.init(new ForgePackForgePlatform());
		PackForgeCore.init();
		PackForgeClient.initClient();
	}
}
