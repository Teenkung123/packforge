package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Shared animation-sheet resize operation used by PackForge's loader and its
 * optional ResourcePack Unbounded fallback provider.
 */
public final class SpriteResize {
	private SpriteResize() {}

	public static NativeImage resize(NativeImage source, int targetWidth, int targetHeight) {
		if (targetWidth <= 0 || targetHeight <= 0) {
			throw new IllegalArgumentException("Target dimensions must be positive");
		}
		if (targetWidth > source.getWidth() || targetHeight > source.getHeight()) {
			throw new IllegalArgumentException("Sprite fallback must not upscale");
		}
		NativeImage scaled = new NativeImage(targetWidth, targetHeight, false);
		try {
			source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), scaled);
			return scaled;
		} catch (Throwable failure) {
			scaled.close();
			throw failure;
		}
	}
}
