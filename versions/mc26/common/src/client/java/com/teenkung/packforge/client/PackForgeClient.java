package com.teenkung.packforge.client;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.atlas.AtlasSplitGuards;
import com.teenkung.packforge.client.atlas.SpriteMetadataCache;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.font.FontBitmapProviderCache;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import com.teenkung.packforge.client.model.ModelParseOptimizer;
import com.teenkung.packforge.client.compat.ResourcePackUnboundedBridge;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.loader.ReloadHooks;
import com.teenkung.packforge.platform.PackForgeCompat;

public final class PackForgeClient {
	public static void initClient() {
		PackForgeConfig.Cfg cfg = PackForgeConfig.get();
		if (cfg.atlasRetryEnabled && cfg.forceDisablePartIIIWithIris && PackForgeCompat.isShaderPipelinePresent()) {
			PackForge.LOGGER.warn("Shader pipeline detected; disabling atlasRetry - set forceDisablePartIIIWithIris=false to override");
			cfg.atlasRetryEnabled = false;
		}
		AtlasSplitGuards.applyStartupGuards();
		ReloadHooks.registerStartHook(SpriteMetadataCache::resetForReload);
		ReloadHooks.registerStartHook(AtlasTimings::resetForReload);
		ReloadHooks.registerStartHook(FontBitmapProviderCache::resetForReload);
		ReloadHooks.registerStartHook(FontSelectionRegistry::resetForReload);
		ReloadHooks.registerStartHook(ModelParseOptimizer::resetForReload);
		ResourcePackUnboundedBridge.registerFallbackProviderIfAvailable();
	}

	private PackForgeClient() {}
}
