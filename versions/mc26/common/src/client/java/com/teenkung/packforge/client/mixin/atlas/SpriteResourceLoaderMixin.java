package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.atlas.AtlasDiagnostics;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;

/** Measures the original stream without substituting Resource identities or decoding behavior. */
@Mixin(SpriteResourceLoader.class)
public interface SpriteResourceLoaderMixin {
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/server/packs/resources/Resource;open()Ljava/io/InputStream;"))
	private static InputStream packforge$timeSpriteStream(Resource resource, Operation<InputStream> original) throws IOException {
		if (!AtlasDiagnostics.observingRead()) return original.call(resource);
		long started = System.nanoTime();
		return AtlasDiagnostics.observeStream(original.call(resource), started);
	}
}
