package com.teenkung.packforge.client.atlas;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** Delegates to vanilla so Forge sprite hooks stay authoritative. */
public final class CappedSpriteResourceLoader implements SpriteResourceLoader {
	private final SpriteResourceLoader delegate;
	private final SpriteMetadataCache.AtlasState state;

	private CappedSpriteResourceLoader(SpriteResourceLoader delegate, SpriteMetadataCache.AtlasState state) {
		this.delegate = delegate;
		this.state = state;
	}

	public static SpriteResourceLoader wrap(SpriteResourceLoader delegate, SpriteMetadataCache.AtlasState state) {
		return new CappedSpriteResourceLoader(delegate, state);
	}

	@Override
	public SpriteContents loadSprite(Identifier spriteLocation, Resource resource) {
		return SpriteMetadataCache.withAtlas(state, () -> delegate.loadSprite(spriteLocation, resource));
	}
}
