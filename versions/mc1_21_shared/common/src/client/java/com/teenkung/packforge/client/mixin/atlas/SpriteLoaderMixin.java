package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.atlas.BoundedSpriteDecode;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@Shadow @Final private ResourceLocation location;

	@WrapOperation(
		method = "loadAndStitch",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private CompletableFuture<List<Function<SpriteResourceLoader, SpriteContents>>> packforge$timeSources(
		Supplier<List<Function<SpriteResourceLoader, SpriteContents>>> sourceSupplier,
		Executor executor,
		Operation<CompletableFuture<List<Function<SpriteResourceLoader, SpriteContents>>>> original
	) {
		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.phaseTimingsEnabled()) {
			return original.call(sourceSupplier, executor);
		}
		Supplier<List<Function<SpriteResourceLoader, SpriteContents>>> timedSupplier = () -> {
			long startNs = AtlasTimings.start();
			List<Function<SpriteResourceLoader, SpriteContents>> suppliers = sourceSupplier.get();
			AtlasTimings.recordSource(this.location, startNs);
			return suppliers;
		};
		return original.call(timedSupplier, executor);
	}

	@WrapMethod(method = "runSpriteSuppliers")
	private static CompletableFuture<List<SpriteContents>> packforge$decodeBatches(
		SpriteResourceLoader loader,
		List<Function<SpriteResourceLoader, SpriteContents>> suppliers,
		Executor executor,
		Operation<CompletableFuture<List<SpriteContents>>> original
	) {
		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.decodeEnabled() && !plan.phaseTimingsEnabled()) {
			return original.call(loader, suppliers, executor);
		}
		String atlas = "unknown";
		long startNs = AtlasTimings.start();
		CompletableFuture<List<SpriteContents>> future = plan.decodeEnabled()
			? BoundedSpriteDecode.decode(suppliers, executor, plan, supplier -> supplier.apply(loader))
			: original.call(loader, suppliers, executor);
		String timingAtlas = atlas;
		return plan.phaseTimingsEnabled()
			? future.whenComplete((ignored, error) -> AtlasTimings.recordDecode(timingAtlas, startNs))
			: future;
	}

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations packforge$timeStitch(
		List<SpriteContents> sprites,
		int mipLevel,
		Executor executor,
		Operation<SpriteLoader.Preparations> original
	) {
		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.phaseTimingsEnabled()) {
			return original.call(sprites, mipLevel, executor);
		}
		String atlas = this.location.toString();
		long startNs = AtlasTimings.start();
		SpriteLoader.Preparations preparations = original.call(sprites, mipLevel, executor);
		AtlasTimings.recordStitch(atlas, startNs);
		long mipStartNs = System.nanoTime();
		preparations.readyForUpload().whenComplete((ignored, error) -> AtlasTimings.recordMip(atlas, mipStartNs));
		return preparations;
	}
}
