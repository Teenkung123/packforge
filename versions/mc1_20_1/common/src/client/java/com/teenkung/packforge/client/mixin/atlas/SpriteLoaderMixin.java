package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.atlas.BoundedSpriteDecode;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@Shadow @Final private ResourceLocation location;

	@Unique private static final Map<List<?>, String> PACKFORGE_ATLAS_BY_SOURCES =
		Collections.synchronizedMap(new WeakHashMap<>());

	@WrapOperation(
		method = "loadAndStitch",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private CompletableFuture<List<Supplier<SpriteContents>>> packforge$timeSources(
		Supplier<List<Supplier<SpriteContents>>> sourceLoader,
		Executor executor,
		Operation<CompletableFuture<List<Supplier<SpriteContents>>>> original
	) {
		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.phaseTimingsEnabled()) {
			return original.call(sourceLoader, executor);
		}
		Supplier<List<Supplier<SpriteContents>>> timedSourceLoader = () -> {
			long startNs = AtlasTimings.start();
			List<Supplier<SpriteContents>> suppliers = sourceLoader.get();
			String atlas = this.location.toString();
			PACKFORGE_ATLAS_BY_SOURCES.put(suppliers, atlas);
			AtlasTimings.recordSource(atlas, startNs);
			return suppliers;
		};
		return original.call(timedSourceLoader, executor);
	}

	@WrapMethod(method = "runSpriteSuppliers")
	private static CompletableFuture<List<SpriteContents>> packforge$decodeBatches(
		List<Supplier<SpriteContents>> suppliers,
		Executor executor,
		Operation<CompletableFuture<List<SpriteContents>>> original
	) {
		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.decodeEnabled() && !plan.phaseTimingsEnabled()) {
			return original.call(suppliers, executor);
		}
		String atlas = PACKFORGE_ATLAS_BY_SOURCES.remove(suppliers);
		if (atlas == null) {
			atlas = "unknown";
		}
		long startNs = AtlasTimings.start();
		CompletableFuture<List<SpriteContents>> future = plan.decodeEnabled()
			? BoundedSpriteDecode.decode(suppliers, executor, plan, Supplier::get)
			: original.call(suppliers, executor);
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
		long stitchStartNs = AtlasTimings.start();
		SpriteLoader.Preparations preparations = original.call(sprites, mipLevel, executor);
		AtlasTimings.recordStitch(atlas, stitchStartNs);
		long mipStartNs = System.nanoTime();
		preparations.readyForUpload().whenComplete((ignored, error) -> AtlasTimings.recordMip(atlas, mipStartNs));
		return preparations;
	}
}
