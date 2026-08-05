package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.client.model.ModelBatchPlan;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@Unique private static final Map<List<?>, String> packforge$supplierOwners =
		Collections.synchronizedMap(new IdentityHashMap<>());

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
		Supplier<List<Function<SpriteResourceLoader, SpriteContents>>> timedSupplier = () -> {
			long startNs = AtlasTimings.start();
			List<Function<SpriteResourceLoader, SpriteContents>> suppliers = sourceSupplier.get();
			AtlasTimings.recordSource(this.location, startNs);
			packforge$supplierOwners.put(suppliers, this.location.toString());
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
		String atlas = packforge$supplierOwners.remove(suppliers);
		if (atlas == null) {
			atlas = "unknown";
		}
		long startNs = AtlasTimings.start();
		CompletableFuture<List<SpriteContents>> future;
		if (!FeatureFlags.atlasDecodeBatchingEnabled()) {
			future = original.call(loader, suppliers, executor);
		} else {
			List<CompletableFuture<List<SpriteContents>>> jobs = new ArrayList<>();
			for (ModelBatchPlan.Range range : ModelBatchPlan.create(
				suppliers.size(),
				Math.max(16, FeatureFlags.atlasDecodeBatchSize()),
				false
			)) {
				List<Function<SpriteResourceLoader, SpriteContents>> batch =
					suppliers.subList(range.fromInclusive(), range.toExclusive());
				jobs.add(CompletableFuture.supplyAsync(() -> {
					List<SpriteContents> decoded = new ArrayList<>(batch.size());
					for (Function<SpriteResourceLoader, SpriteContents> supplier : batch) {
						SpriteContents sprite = supplier.apply(loader);
						if (sprite != null) {
							decoded.add(sprite);
						}
					}
					return List.copyOf(decoded);
				}, executor));
			}
			future = Util.sequence(jobs).thenApply(batches -> batches.stream().flatMap(List::stream).toList());
		}
		String timingAtlas = atlas;
		return future.whenComplete((ignored, error) -> AtlasTimings.recordDecode(timingAtlas, startNs));
	}

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations packforge$timeStitch(
		List<SpriteContents> sprites,
		int mipLevel,
		Executor executor,
		Operation<SpriteLoader.Preparations> original
	) {
		String atlas = this.location.toString();
		long startNs = AtlasTimings.start();
		SpriteLoader.Preparations preparations = original.call(sprites, mipLevel, executor);
		AtlasTimings.recordStitch(atlas, startNs);
		if (FeatureFlags.atlasPhaseTimingsEnabled()) {
			long mipStartNs = System.nanoTime();
			preparations.readyForUpload().whenComplete((ignored, error) -> AtlasTimings.recordMip(atlas, mipStartNs));
		}
		return preparations;
	}
}
