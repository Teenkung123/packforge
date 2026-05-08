package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.atlas.AtlasReport;
import com.teenkung.packforge.client.atlas.AtlasRetry;
import com.teenkung.packforge.client.atlas.CappedSpriteResourceLoader;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.StitcherException;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@Shadow @Final private Identifier location;

	@Redirect(
		method = "loadAndStitch",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/texture/atlas/SpriteResourceLoader;create(Ljava/util/Set;)Lnet/minecraft/client/renderer/texture/atlas/SpriteResourceLoader;"
		)
	)
	private SpriteResourceLoader packforge$wrapLoader(Set<MetadataSectionType<?>> additional) {
		if (!FeatureFlags.atlasCapEnabled() || FeatureFlags.atlasExcludes(this.location.toString())) {
			return SpriteResourceLoader.create(additional);
		}
		return new CappedSpriteResourceLoader(this.location, additional);
	}

	@Inject(method = "loadAndStitch", at = @At("RETURN"))
	private void packforge$reportAtlas(CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> cir) {
		AtlasReport.logAtlas(this.location);
	}

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations packforge$retryStitch(List<SpriteContents> sprites, int maxMipmapLevels, Executor executor, Operation<SpriteLoader.Preparations> original) {
		if (!FeatureFlags.atlasRetryEnabled() || FeatureFlags.atlasExcludes(this.location.toString())) {
			return original.call(sprites, maxMipmapLevels, executor);
		}
		int maxAttempts = FeatureFlags.atlasRetryMaxAttempts();
		List<SpriteContents> current = sprites;
		ReportedException last = null;
		for (int attempt = 0; attempt <= maxAttempts; attempt++) {
			try {
				return original.call(current, maxMipmapLevels, executor);
			} catch (ReportedException e) {
				if (!(e.getCause() instanceof StitcherException)) throw e;
				last = e;
				if (attempt == maxAttempts) break;
				PackForge.LOGGER.warn("PackForge atlas {} stitch failed (attempt {}); retrying with halved sprites", this.location, attempt + 1);
				current = AtlasRetry.halveAll(current, this.location);
			}
		}
		PackForge.LOGGER.error("PackForge atlas {} retry exhausted after {} attempts", this.location, maxAttempts);
		throw last;
	}
}
