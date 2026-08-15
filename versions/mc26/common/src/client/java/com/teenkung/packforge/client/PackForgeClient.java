package com.teenkung.packforge.client;

import com.teenkung.packforge.client.atlas.AtlasSplitGuards;
import com.teenkung.packforge.client.atlas.SpriteMetadataCache;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.font.FontBitmapProviderCache;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import com.teenkung.packforge.client.compat.ResourcePackUnboundedBridge;
import com.teenkung.packforge.loader.ReloadHooks;

public final class PackForgeClient {
	public static void initClient() {
		AtlasSplitGuards.applyStartupGuards();
		ReloadHooks.registerStartHook(SpriteMetadataCache::resetForReload);
		ReloadHooks.registerStartHook(AtlasTimings::resetForReload);
		ReloadHooks.registerStartHook(FontBitmapProviderCache::resetForReload);
		ReloadHooks.registerStartHook(FontSelectionRegistry::resetForReload);
		ResourcePackUnboundedBridge.registerFallbackProviderIfAvailable();
	}

	private PackForgeClient() {}
}
