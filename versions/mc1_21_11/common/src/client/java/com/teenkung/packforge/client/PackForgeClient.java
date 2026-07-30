package com.teenkung.packforge.client;

import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.font.FontBitmapProviderCache;
import com.teenkung.packforge.client.font.FontOptimizationState;
import com.teenkung.packforge.client.model.ModelParseOptimizer;
import com.teenkung.packforge.loader.ReloadHooks;

public final class PackForgeClient {
	public static void initClient() {
		ReloadHooks.registerStartHook(AtlasTimings::resetForReload);
		ReloadHooks.registerStartHook(FontBitmapProviderCache::resetForReload);
		ReloadHooks.registerStartHook(FontOptimizationState::resetForReload);
		ReloadHooks.registerStartHook(ModelParseOptimizer::resetForReload);
	}

	private PackForgeClient() {}
}
