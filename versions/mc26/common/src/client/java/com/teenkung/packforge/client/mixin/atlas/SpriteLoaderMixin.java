package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.client.atlas.AtlasReport;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.atlas.BoundedSpriteDecode;
import com.teenkung.packforge.client.atlas.CappedSpriteResourceLoader;
import com.teenkung.packforge.client.compat.ResourcePackUnboundedBridge;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
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

/** Narrow mc26 sprite hooks; vanilla load/stitch control flow remains authoritative. */
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
		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (packforge$resourcePackUnboundedOwnsAtlas()
			|| !plan.atlasCapApplies(this.location.toString())) {
			return SpriteResourceLoader.create(additional);
		}
		return new CappedSpriteResourceLoader(this.location, additional);
	}

	@Inject(method = "loadAndStitch", at = @At("RETURN"))
	private void packforge$reportAtlas(CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> cir) {
		cir.getReturnValue().thenRun(() -> {
			if (!packforge$resourcePackUnboundedOwnsAtlas()) {
				AtlasReport.logAtlas(this.location);
			}
			AtlasTimings.logAtlas(this.location);
		});
	}

	@WrapMethod(method = "runSpriteSuppliers")
	private CompletableFuture<List<SpriteContents>> packforge$decodeBounded(
		SpriteResourceLoader resourceLoader,
		List<SpriteSource.Loader> loaders,
		Executor executor,
		Operation<CompletableFuture<List<SpriteContents>>> original
	) {
		if (packforge$resourcePackUnboundedOwnsAtlas()) {
			return original.call(resourceLoader, loaders, executor);
		}

		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.decodeEnabled() && !plan.phaseTimingsEnabled()) {
			return original.call(resourceLoader, loaders, executor);
		}

		long startNs = AtlasTimings.start();
		CompletableFuture<List<SpriteContents>> future = plan.decodeEnabled()
			? BoundedSpriteDecode.decode(loaders, executor, plan, loader -> loader.get(resourceLoader))
			: original.call(resourceLoader, loaders, executor);
		return plan.phaseTimingsEnabled()
			? future.whenComplete((ignored, error) -> AtlasTimings.recordDecode(this.location, startNs))
			: future;
	}

	private boolean packforge$resourcePackUnboundedOwnsAtlas() {
		return ResourcePackUnboundedBridge.configuredOwner(this.location);
	}
}
