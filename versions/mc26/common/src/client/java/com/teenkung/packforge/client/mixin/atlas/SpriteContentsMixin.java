package com.teenkung.packforge.client.mixin.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.teenkung.packforge.client.atlas.MipSpriteAccess;
import com.teenkung.packforge.client.atlas.SpriteMetadataCache;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/** Records retry-owned images and exposes existing mip image ownership for bounded preparation. */
@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin implements MipSpriteAccess {
	@Shadow private NativeImage[] byMipLevel;

	@Override public NativeImage[] packforge$mipImages() { return byMipLevel; }

	@Inject(
		method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
		at = @At("TAIL")
	)
	private void packforge$recordContents(
		Identifier sprite,
		FrameSize frameSize,
		NativeImage image,
		Optional<AnimationMetadataSection> animation,
		List<MetadataSectionType.WithValue<?>> additional,
		Optional<TextureMetadataSection> texture,
		CallbackInfo ci
	) {
		SpriteMetadataCache.recordConstructed(
			(SpriteContents) (Object) this,
			sprite,
			frameSize,
			image,
			animation,
			additional,
			texture
		);
	}

	@Inject(
		method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;)V",
		at = @At("TAIL")
	)
	private void packforge$recordSimpleContents(
		Identifier sprite,
		FrameSize frameSize,
		NativeImage image,
		CallbackInfo ci
	) {
		SpriteMetadataCache.recordConstructed(
			(SpriteContents) (Object) this,
			sprite,
			frameSize,
			image,
			Optional.empty(),
			List.of(),
			Optional.empty()
		);
	}
}
