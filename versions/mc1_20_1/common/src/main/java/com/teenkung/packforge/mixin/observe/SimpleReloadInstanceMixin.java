package com.teenkung.packforge.mixin.observe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.ReloadListenerTelemetry;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SimpleReloadInstance.class)
public abstract class SimpleReloadInstanceMixin {
	@WrapOperation(
		method = "method_18368",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/PreparableReloadListener;reload(Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/util/profiling/ProfilerFiller;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private static CompletableFuture<Void> packforge$trackReloadStep(
		PreparableReloadListener listener,
		PreparableReloadListener.PreparationBarrier barrier,
		ResourceManager resourceManager,
		ProfilerFiller preparationProfiler,
		ProfilerFiller reloadProfiler,
		Executor preparationExecutor,
		Executor reloadExecutor,
		Operation<CompletableFuture<Void>> original
	) {
		String name = listener.getName();
		Executor trackedPreparation = command -> preparationExecutor.execute(ReloadListenerTelemetry.prepare(name, command));
		Executor trackedReload = command -> reloadExecutor.execute(ReloadListenerTelemetry.apply(name, command));
		boolean recordWall = FeatureFlags.reloadListenerTimingsEnabled();
		long startNs = recordWall ? System.nanoTime() : 0L;
		CompletableFuture<Void> future = original.call(
			listener,
			barrier,
			resourceManager,
			preparationProfiler,
			reloadProfiler,
			trackedPreparation,
			trackedReload
		);
		return recordWall
			? future.whenComplete((result, error) -> LoaderTimings.recordListenerWall(name, System.nanoTime() - startNs))
			: future;
	}
}
