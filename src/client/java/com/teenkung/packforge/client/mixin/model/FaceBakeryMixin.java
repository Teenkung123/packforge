package com.teenkung.packforge.client.mixin.model;

import com.mojang.blaze3d.platform.Transparency;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FaceBakery.class)
public abstract class FaceBakeryMixin {
	@Redirect(
		method = "computeMaterialTransparency",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/texture/SpriteContents;computeTransparency(FFFF)Lcom/mojang/blaze3d/platform/Transparency;"
		)
	)
	private static Transparency packforge$clampTransparencyProbe(SpriteContents contents, float u0, float v0, float u1, float v1) {
		if (!FeatureFlags.modelUvTransparencyClampEnabled()) {
			return contents.computeTransparency(u0, v0, u1, v1);
		}
		float minU = Math.clamp(Math.min(u0, u1), 0.0f, 1.0f);
		float minV = Math.clamp(Math.min(v0, v1), 0.0f, 1.0f);
		float maxU = Math.clamp(Math.max(u0, u1), 0.0f, 1.0f);
		float maxV = Math.clamp(Math.max(v0, v1), 0.0f, 1.0f);
		return contents.computeTransparency(minU, minV, maxU, maxV);
	}
}
