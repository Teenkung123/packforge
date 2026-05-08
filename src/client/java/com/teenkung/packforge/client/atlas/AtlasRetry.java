package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class AtlasRetry {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static List<SpriteContents> halveAll(List<SpriteContents> input, Identifier atlas) {
		List<SpriteContents> out = new ArrayList<>(input.size());
		int halved = 0;
		for (SpriteContents s : input) {
			SpriteContents h = halveOne(s);
			if (h != s) halved++;
			out.add(h);
		}
		LOGGER.warn("PackForge atlas {} retry: halved {}/{} sprites", atlas, halved, input.size());
		return out;
	}

	private static SpriteContents halveOne(SpriteContents original) {
		Identifier id = original.name();
		SpriteMetadataCache.RetainedSprite r = SpriteMetadataCache.getRetained(id);
		if (r == null) return original;

		int curW = r.image.getWidth();
		int curH = r.image.getHeight();
		int fW = r.frameSize.width();
		int fH = r.frameSize.height();
		if (curW < 2 || curH < 2 || (fW % 2) != 0 || (fH % 2) != 0) return original;
		if ((curW % 2) != 0 || (curH % 2) != 0) return original;
		int newW = curW / 2;
		int newH = curH / 2;
		if (newW < 1 || newH < 1) return original;

		NativeImage scaled;
		try {
			scaled = new NativeImage(newW, newH, false);
			r.image.resizeSubRectTo(0, 0, curW, curH, scaled);
		} catch (Throwable t) {
			LOGGER.warn("PackForge halve failed for {}", id, t);
			return original;
		}

		FrameSize newFrame = new FrameSize(fW / 2, fH / 2);
		r.image = scaled;
		r.frameSize = newFrame;
		r.appliedScale = r.appliedScale * 2;

		SpriteContents rebuilt = r.toSpriteContents();
		try {
			NativeImage clone = new NativeImage(newW, newH, false);
			scaled.resizeSubRectTo(0, 0, newW, newH, clone);
			r.image = clone;
		} catch (Throwable ignored) {}
		return rebuilt;
	}

	private AtlasRetry() {}
}
