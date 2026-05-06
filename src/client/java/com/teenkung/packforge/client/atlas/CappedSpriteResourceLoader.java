package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CappedSpriteResourceLoader implements SpriteResourceLoader {
	private static final Logger LOGGER = LogUtils.getLogger();

	private final Identifier atlasId;
	private final Set<MetadataSectionType<?>> additionalMetadataSections;

	public CappedSpriteResourceLoader(Identifier atlasId, Set<MetadataSectionType<?>> additionalMetadataSections) {
		this.atlasId = atlasId;
		this.additionalMetadataSections = additionalMetadataSections;
	}

	@Override
	public SpriteContents loadSprite(Identifier spriteLocation, Resource resource) {
		Optional<AnimationMetadataSection> animationInfo;
		Optional<TextureMetadataSection> textureInfo;
		List<MetadataSectionType.WithValue<?>> additionalMetadata;
		try {
			ResourceMetadata metadata = resource.metadata();
			animationInfo = metadata.getSection(AnimationMetadataSection.TYPE);
			textureInfo = metadata.getSection(TextureMetadataSection.TYPE);
			additionalMetadata = metadata.getTypedSections(additionalMetadataSections);
		} catch (Exception e) {
			LOGGER.error("Unable to parse metadata from {}", spriteLocation, e);
			return null;
		}

		NativeImage image;
		try (InputStream is = resource.open()) {
			image = NativeImage.read(is);
		} catch (IOException e) {
			LOGGER.error("Using missing texture, unable to load {}", spriteLocation, e);
			return null;
		}

		FrameSize frameSize;
		if (animationInfo.isPresent()) {
			frameSize = animationInfo.get().calculateFrameSize(image.getWidth(), image.getHeight());
			if (!Mth.isMultipleOf(image.getWidth(), frameSize.width()) || !Mth.isMultipleOf(image.getHeight(), frameSize.height())) {
				LOGGER.error("Image {} size {},{} is not multiple of frame size {},{}", spriteLocation, image.getWidth(), image.getHeight(), frameSize.width(), frameSize.height());
				image.close();
				return null;
			}
		} else {
			frameSize = new FrameSize(image.getWidth(), image.getHeight());
		}

		SpriteMetadataCache.recordSprite(atlasId);

		int cap = FeatureFlags.atlasCapPx();
		int scale = SpriteCap.computeScale(frameSize.width(), frameSize.height(), cap);
		if (scale > 1) {
			int newImgW = image.getWidth() / scale;
			int newImgH = image.getHeight() / scale;
			NativeImage scaled;
			try {
				scaled = new NativeImage(newImgW, newImgH, false);
				image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), scaled);
			} catch (Throwable t) {
				LOGGER.warn("PackForge cap failed for {}; keeping original size", spriteLocation, t);
				return new SpriteContents(spriteLocation, frameSize, image, animationInfo, additionalMetadata, textureInfo);
			}
			int origFW = frameSize.width();
			int origFH = frameSize.height();
			frameSize = new FrameSize(origFW / scale, origFH / scale);
			SpriteMetadataCache.recordCap(spriteLocation, atlasId, scale, origFW, origFH, frameSize.width(), frameSize.height());
			image.close();
			image = scaled;
		}

		return new SpriteContents(spriteLocation, frameSize, image, animationInfo, additionalMetadata, textureInfo);
	}
}
