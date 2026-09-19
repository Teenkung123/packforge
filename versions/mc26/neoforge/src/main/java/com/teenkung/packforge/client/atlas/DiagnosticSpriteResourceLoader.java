package com.teenkung.packforge.client.atlas;

import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;

/** Preserves NeoForge's caller-supplied sprite constructor without routing through the two-argument default. */
public final class DiagnosticSpriteResourceLoader {
	public static SpriteResourceLoader wrap(SpriteResourceLoader delegate, AtlasDiagnostics diagnostics) {
		return (id, resource, constructor) -> diagnostics.observeSprite(id, () -> delegate.loadSprite(id, resource, constructor));
	}

	private DiagnosticSpriteResourceLoader() {}
}
