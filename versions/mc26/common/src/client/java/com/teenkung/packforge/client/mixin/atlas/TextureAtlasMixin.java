package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.client.atlas.AtlasDiagnostics;
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
		AtlasDiagnostics diagnostics = AtlasDiagnostics.capture(this.location.toString());
		AtlasDiagnostics.Sample sample = diagnostics == null ? null : AtlasDiagnostics.start();
		Throwable failure = null;
		try {
			original.call(preparations);
		} catch (RuntimeException | Error error) {
			failure = error;
			throw error;
		} finally {
			if (diagnostics != null) {
				try { diagnostics.stage("upload", sample, failure); }
				finally { diagnostics.close(); }
			}
		}
	}
}
