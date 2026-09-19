package com.teenkung.packforge.fabric;

import com.teenkung.packforge.client.PackForgeClient;
import net.fabricmc.api.ClientModInitializer;

public final class PackForgeFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PackForgeClient.initClient();
	}
}
