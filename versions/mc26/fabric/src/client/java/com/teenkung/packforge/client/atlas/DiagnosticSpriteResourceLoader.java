package com.teenkung.packforge.client.atlas;

import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;

/** Physical adapter for Fabric's two-argument sprite loader. */
public final class DiagnosticSpriteResourceLoader {
	public static SpriteResourceLoader wrap(SpriteResourceLoader delegate, AtlasDiagnostics diagnostics) {
		return (id, resource) -> diagnostics.observeSprite(id, () -> delegate.loadSprite(id, resource));
	}

	private DiagnosticSpriteResourceLoader() {}
}
