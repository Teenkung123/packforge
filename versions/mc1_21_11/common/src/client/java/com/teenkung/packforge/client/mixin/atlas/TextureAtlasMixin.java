package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
	@Shadow @Final private Identifier location;

	@WrapMethod(method = "upload")
	private void packforge$timeUpload(SpriteLoader.Preparations preparations, Operation<Void> original) {
		long startNs = AtlasTimings.start();
		try {
			original.call(preparations);
		} finally {
			String atlas = this.location.toString();
			AtlasTimings.recordUpload(atlas, startNs);
			AtlasTimings.logAtlas(atlas);
		}
	}
}
