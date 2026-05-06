package com.teenkung.packforge.mixin.observe;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SimpleReloadInstance.class)
public abstract class SimpleReloadInstanceMixin {
	@Redirect(
		method = "prepareTasks",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;create(Lnet/minecraft/server/packs/resources/PreparableReloadListener$SharedState;Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Lnet/minecraft/server/packs/resources/PreparableReloadListener;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private CompletableFuture<?> packforge$trackReloadStep(
		@Coerce Object stateFactory,
		PreparableReloadListener.SharedState sharedState,
		PreparableReloadListener.PreparationBarrier barrier,
		PreparableReloadListener listener,
		Executor taskExecutor,
		Executor reloadExecutor
	) {
		String name = listener.getName();
		Executor trackedTaskExecutor = command -> taskExecutor.execute(() -> {
			ReloadStatus.prepareStarted(name);
			long startNs = System.nanoTime();
			try {
				command.run();
			} finally {
				LoaderTimings.recordListenerPrepare(name, System.nanoTime() - startNs);
				ReloadStatus.prepareFinished();
			}
		});
		Executor trackedReloadExecutor = command -> reloadExecutor.execute(() -> {
			ReloadStatus.applyStarted(name);
			long startNs = System.nanoTime();
			try {
				command.run();
			} finally {
				LoaderTimings.recordListenerApply(name, System.nanoTime() - startNs);
				ReloadStatus.applyFinished();
			}
		});
		try {
			Method create = stateFactory.getClass().getMethod(
				"create",
				PreparableReloadListener.SharedState.class,
				PreparableReloadListener.PreparationBarrier.class,
				PreparableReloadListener.class,
				Executor.class,
				Executor.class
			);
			create.setAccessible(true);
			long startNs = System.nanoTime();
			CompletableFuture<?> future = (CompletableFuture<?>) create.invoke(stateFactory, sharedState, barrier, listener, trackedTaskExecutor, trackedReloadExecutor);
			return future.whenComplete((result, error) -> LoaderTimings.recordListenerWall(name, System.nanoTime() - startNs));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new IllegalStateException("PackForge could not wrap reload listener status", e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) throw runtime;
			if (cause instanceof Error error) throw error;
			PackForge.LOGGER.warn("Reload listener threw checked exception while PackForge status tracking was active", cause);
			throw new RuntimeException(cause);
		}
	}
}
