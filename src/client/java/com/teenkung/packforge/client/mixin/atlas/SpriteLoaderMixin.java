package com.teenkung.packforge.client.mixin.atlas;

import com.teenkung.packforge.client.atlas.AtlasReport;
import com.teenkung.packforge.client.atlas.CappedSpriteResourceLoader;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.renderer.texture.SpriteLoader;
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

import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
}
