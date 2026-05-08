package com.teenkung.packforge.client;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.atlas.SpriteMetadataCache;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.loader.ReloadHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class PackForgeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PackForgeConfig.Cfg cfg = PackForgeConfig.get();
		if (cfg.atlasRetryEnabled && cfg.forceDisablePartIIIWithIris && FabricLoader.getInstance().isModLoaded("iris")) {
			PackForge.LOGGER.warn("Iris detected; disabling atlasRetry - set forceDisablePartIIIWithIris=false to override");
			cfg.atlasRetryEnabled = false;
		}
		ReloadHooks.registerStartHook(SpriteMetadataCache::resetForReload);
	}
}
