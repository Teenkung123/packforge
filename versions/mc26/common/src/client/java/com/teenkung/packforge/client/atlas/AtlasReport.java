package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.PackForge;
import net.minecraft.resources.Identifier;

public final class AtlasReport {
	public static void logAtlas(Identifier atlasId, SpriteMetadataCache.AtlasState state) {
		if (state == null) {
			return;
		}
		int sprites = state.spriteCount();
		int replaced = state.replacementCount();
		if (sprites == 0 && replaced == 0) {
			return;
		}
		PackForge.LOGGER.info(
			"PackForge atlas {}: sprites={} replacements={} stitchAttempts={}",
			atlasId,
			sprites,
			replaced,
			state.stitchAttempts()
		);
	}

	private AtlasReport() {}
}
