package com.teenkung.packforge.mixin.observe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.ReloadListenerTelemetry;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SimpleReloadInstance.class)
public abstract class SimpleReloadInstanceMixin {
	@WrapOperation(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;create(Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/server/packs/resources/PreparableReloadListener;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private CompletableFuture<?> packforge$telemetry(
		@Coerce Object factory,
		PreparableReloadListener.PreparationBarrier barrier,
		ResourceManager manager,
		PreparableReloadListener listener,
		Executor preparationExecutor,
		Executor applyExecutor,
		Operation<CompletableFuture<?>> original
	) {
		String name = listener.getName();
		Executor trackedPreparation = command ->
			preparationExecutor.execute(ReloadListenerTelemetry.prepare(name, command));
		Executor trackedApply = command ->
			applyExecutor.execute(ReloadListenerTelemetry.apply(name, command));
		long started = FeatureFlags.reloadListenerTimingsEnabled() ? System.nanoTime() : 0L;
		CompletableFuture<?> future = original.call(
			factory,
			barrier,
			manager,
			listener,
			trackedPreparation,
			trackedApply
		);
		return started == 0L
			? future
			: future.whenComplete((ignored, error) ->
				LoaderTimings.recordListenerWall(name, System.nanoTime() - started));
	}
}
