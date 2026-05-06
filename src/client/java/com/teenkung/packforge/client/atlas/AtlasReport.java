package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.PackForge;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicInteger;

public final class AtlasReport {
	public static void logAtlas(Identifier atlasId) {
		AtomicInteger spriteCount = SpriteMetadataCache.spriteCountByAtlas().get(atlasId);
		AtomicInteger downCount = SpriteMetadataCache.downscaledByAtlas().get(atlasId);
		int sprites = spriteCount == null ? 0 : spriteCount.get();
		int downscaled = downCount == null ? 0 : downCount.get();
		if (sprites == 0 && downscaled == 0) return;
		PackForge.LOGGER.info("PackForge atlas {}: sprites={} downscaled={}", atlasId, sprites, downscaled);
	}

	private AtlasReport() {}
}
