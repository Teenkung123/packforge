package com.teenkung.packforge.neoforge;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.PackForgeCore;
import com.teenkung.packforge.client.PackForgeClient;
import com.teenkung.packforge.platform.PackForgeServices;
import net.neoforged.fml.common.Mod;

@Mod(PackForge.MOD_ID)
public final class PackForgeNeoForge {
	public PackForgeNeoForge() {
		PackForgeServices.init(new NeoForgePackForgePlatform());
		PackForgeCore.init();
		PackForgeClient.initClient();
	}
}
