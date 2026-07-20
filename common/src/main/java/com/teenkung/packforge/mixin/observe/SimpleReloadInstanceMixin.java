package com.teenkung.packforge.mixin.observe;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.ReloadStatus;
import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;
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
		boolean trackStartup = StartupTimings.isActive();
		if (!FeatureFlags.reloadListenerTimingsEnabled() && !FeatureFlags.loadingStatusOverlayEnabled() && !trackStartup) {
			return packforge$invokeCreate(stateFactory, sharedState, barrier, listener, taskExecutor, reloadExecutor, name, false);
		}
		Executor trackedTaskExecutor = command -> taskExecutor.execute(() -> {
			boolean startupActive = trackStartup && StartupTimings.isActive();
			if (FeatureFlags.loadingStatusOverlayEnabled()) {
				ReloadStatus.prepareStarted(name);
			}
			if (FeatureFlags.startupStatusOverlayEnabled() && startupActive) {
				StartupStatus.update("Preparing", readableStartupName(name));
			}
			long startNs = (FeatureFlags.reloadListenerTimingsEnabled() || startupActive) ? System.nanoTime() : 0L;
			try {
				command.run();
			} finally {
				if (FeatureFlags.reloadListenerTimingsEnabled()) {
					LoaderTimings.recordListenerPrepare(name, System.nanoTime() - startNs);
				}
				if (startupActive) {
					StartupTimings.recordDuration("prepare " + readableStartupName(name), System.nanoTime() - startNs);
				}
				if (FeatureFlags.loadingStatusOverlayEnabled()) {
					ReloadStatus.prepareFinished();
				}
			}
		});
		Executor trackedReloadExecutor = command -> reloadExecutor.execute(() -> {
			boolean startupActive = trackStartup && StartupTimings.isActive();
			if (FeatureFlags.loadingStatusOverlayEnabled()) {
				ReloadStatus.applyStarted(name);
			}
			if (FeatureFlags.startupStatusOverlayEnabled() && startupActive) {
				StartupStatus.update("Applying", readableStartupName(name));
			}
			long startNs = (FeatureFlags.reloadListenerTimingsEnabled() || startupActive) ? System.nanoTime() : 0L;
			try {
				command.run();
			} finally {
				if (FeatureFlags.reloadListenerTimingsEnabled()) {
					LoaderTimings.recordListenerApply(name, System.nanoTime() - startNs);
				}
				if (startupActive) {
					StartupTimings.recordDuration("apply " + readableStartupName(name), System.nanoTime() - startNs);
				}
				if (FeatureFlags.loadingStatusOverlayEnabled()) {
					ReloadStatus.applyFinished();
				}
			}
		});
		return packforge$invokeCreate(stateFactory, sharedState, barrier, listener, trackedTaskExecutor, trackedReloadExecutor, name, FeatureFlags.reloadListenerTimingsEnabled());
	}

	private CompletableFuture<?> packforge$invokeCreate(
		Object stateFactory,
		PreparableReloadListener.SharedState sharedState,
		PreparableReloadListener.PreparationBarrier barrier,
		PreparableReloadListener listener,
		Executor taskExecutor,
		Executor reloadExecutor,
		String name,
		boolean recordWall
	) {
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
			long startNs = recordWall ? System.nanoTime() : 0L;
			CompletableFuture<?> future = (CompletableFuture<?>) create.invoke(stateFactory, sharedState, barrier, listener, taskExecutor, reloadExecutor);
			return recordWall ? future.whenComplete((result, error) -> LoaderTimings.recordListenerWall(name, System.nanoTime() - startNs)) : future;
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

	private static String readableStartupName(String listenerName) {
		if (listenerName == null || listenerName.isBlank()) {
			return "resources";
		}
		return switch (listenerName) {
			case "AtlasManager" -> "texture atlases";
			case "ModelManager" -> "models";
			case "TextureManager" -> "textures";
			case "SoundManager" -> "sounds";
			case "LanguageManager" -> "languages";
			case "FontManager" -> "fonts";
			case "BlockColors" -> "block colors";
			case "ItemColors" -> "item colors";
			default -> listenerName;
		};
	}
}
