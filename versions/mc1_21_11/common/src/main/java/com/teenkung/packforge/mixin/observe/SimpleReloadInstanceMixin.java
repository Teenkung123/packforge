package com.teenkung.packforge.mixin.observe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadListenerTelemetry;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SimpleReloadInstance.class)
public abstract class SimpleReloadInstanceMixin {
	@WrapOperation(method = "prepareTasks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;create(Lnet/minecraft/server/packs/resources/PreparableReloadListener$SharedState;Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Lnet/minecraft/server/packs/resources/PreparableReloadListener;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<?> packforge$telemetry(@Coerce Object factory, PreparableReloadListener.SharedState state, PreparableReloadListener.PreparationBarrier barrier, PreparableReloadListener listener, Executor prepare, Executor apply, Operation<CompletableFuture<?>> original) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context == null) {
			return original.call(factory, state, barrier, listener, prepare, apply);
		}
		String name = ReloadListenerTelemetry.canonicalName(listener.getName());
		long startedNs = context.features().reloadListenerTimingsEnabled() ? System.nanoTime() : 0L;
		Executor trackedPrepare = ReloadListenerTelemetry.prepareExecutor(context, name, prepare);
		Executor trackedApply = ReloadListenerTelemetry.applyExecutor(context, name, apply);
		CompletableFuture<?> result = original.call(factory, state, barrier, listener, trackedPrepare, trackedApply);
		return ReloadListenerTelemetry.observeListenerFuture(context, name, result, startedNs);
	}
}
