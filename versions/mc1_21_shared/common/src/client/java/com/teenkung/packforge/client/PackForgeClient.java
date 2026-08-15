package com.teenkung.packforge.client;

import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.font.FontBitmapProviderCache;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import com.teenkung.packforge.loader.ReloadHooks;

public final class PackForgeClient {
	public static void initClient() {
		ReloadHooks.registerStartHook(AtlasTimings::resetForReload);
		ReloadHooks.registerStartHook(FontBitmapProviderCache::resetForReload);
		ReloadHooks.registerStartHook(FontSelectionRegistry::resetForReload);
	}

	private PackForgeClient() {}
}
