package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
	@WrapMethod(method = "upload")
	private void packforge$timeUpload(SpriteLoader.Preparations preparations, Operation<Void> original) {
		TextureAtlas atlas = (TextureAtlas)(Object)this;
		long startNs = AtlasTimings.start();
		original.call(preparations);
		String location = atlas.location().toString();
		AtlasTimings.recordUpload(location, startNs);
		AtlasTimings.logAtlas(location);
	}
}
