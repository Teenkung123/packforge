package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.platform.PackForgeCompat;

import java.util.ArrayList;
import java.util.List;

public final class AtlasSplitGuards {
	public static void applyStartupGuards() {
		PackForgeConfig.Cfg cfg = PackForgeConfig.get();
		boolean changed = removeUnsafeTargets(cfg);

		if (cfg.experimentalAtlasSplit && cfg.atlasSplitDisableWithIris && PackForgeCompat.isShaderPipelinePresent()) {
			PackForge.LOGGER.warn("Shader pipeline detected; disabling experimental atlas split. Downscale/cap fallback stays active.");
			cfg.experimentalAtlasSplit = false;
			changed = true;
		}
		if (cfg.experimentalAtlasSplit && cfg.atlasSplitDisableWithSodium && PackForgeCompat.isSodiumLikePresent()) {
			PackForge.LOGGER.warn("Sodium-like renderer detected; disabling experimental atlas split because atlasSplitDisableWithSodium=true.");
			cfg.experimentalAtlasSplit = false;
			changed = true;
		}
		if (cfg.experimentalAtlasSplit && PackForgeCompat.isSodiumLikePresent()) {
			PackForge.LOGGER.warn("Experimental atlas split is enabled with Sodium-like renderer loaded; item/particle testing only, blocks remain unsupported.");
		}
		if (cfg.experimentalAtlasSplit && cfg.atlasSplitTargets.isEmpty()) {
			PackForge.LOGGER.warn("Experimental atlas split enabled with no safe targets; disabling.");
			cfg.experimentalAtlasSplit = false;
			changed = true;
		}
		if (cfg.experimentalAtlasSplit) {
			PackForge.LOGGER.warn("Experimental atlas split is config-gated only in this build; blocks are never split, fallback downscale remains primary.");
		}
		if (changed) {
			PackForgeConfig.save();
		}
	}

	private static boolean removeUnsafeTargets(PackForgeConfig.Cfg cfg) {
		List<String> safe = new ArrayList<>();
		boolean changed = false;
		for (String target : cfg.atlasSplitTargets) {
			String normalized = target == null ? "" : target.trim().toLowerCase();
			if (normalized.equals("minecraft:blocks")) {
				PackForge.LOGGER.warn("Ignoring atlas split target minecraft:blocks; block terrain split is unsupported by PackForge.");
				changed = true;
				continue;
			}
			if ((normalized.equals("minecraft:items") || normalized.equals("minecraft:particles")) && !safe.contains(normalized)) {
				safe.add(normalized);
			} else {
				changed = true;
			}
		}
		if (!safe.equals(cfg.atlasSplitTargets)) {
			cfg.atlasSplitTargets = safe;
			changed = true;
		}
		return changed;
	}

	private AtlasSplitGuards() {}
}
