package com.teenkung.packforge.client;

import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.font.FontBitmapProviderCache;
import com.teenkung.packforge.client.font.FontOptimizationState;
import com.teenkung.packforge.config.PackForgeCapabilities;
import com.teenkung.packforge.config.PackForgeCapability;
import com.teenkung.packforge.loader.ReloadHooks;

import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_DECODE_BATCHING;
import static com.teenkung.packforge.config.PackForgeCapability.ATLAS_PHASE_TIMINGS;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_BITMAP_CACHE;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_PROVIDER_PRESELECTION;
import static com.teenkung.packforge.config.PackForgeCapability.FONT_RELOAD_DIAGNOSTICS;
import static com.teenkung.packforge.config.PackForgeCapability.MODEL_PARSE_BATCHING;

public final class PackForgeClient {
	public static void initClient() {
		if (supportsAny(ATLAS_PHASE_TIMINGS, ATLAS_DECODE_BATCHING)) {
			ReloadHooks.registerStartHook(AtlasTimings::resetForReload);
		}
		if (supportsAny(FONT_RELOAD_DIAGNOSTICS, FONT_PROVIDER_PRESELECTION, FONT_BITMAP_CACHE)) {
			ReloadHooks.registerStartHook(FontBitmapProviderCache::resetForReload);
			ReloadHooks.registerStartHook(FontOptimizationState::resetForReload);
		}
	}

	private static boolean supportsAny(PackForgeCapability... capabilities) {
		for (PackForgeCapability capability : capabilities) {
			if (PackForgeCapabilities.supports(capability)) {
				return true;
			}
		}
		return false;
	}

	private PackForgeClient() {}
}
