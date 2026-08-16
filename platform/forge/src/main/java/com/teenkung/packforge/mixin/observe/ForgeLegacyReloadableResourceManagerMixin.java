package com.teenkung.packforge.mixin.observe;

import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadLifecycle;
import com.teenkung.packforge.loader.RuntimeResourceHash;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Forge 49's legacy runtime is more reliable with direct lifecycle injections than WrapMethod. */
@Mixin(ReloadableResourceManager.class)
public abstract class ForgeLegacyReloadableResourceManagerMixin {
	@Unique
	private static final ThreadLocal<ReloadExecutionContext> PACKFORGE_CONTEXT = new ThreadLocal<>();

	@Inject(method = "createReload", at = @At("HEAD"))
	private void packforge$startReload(
		Executor preparationExecutor,
		Executor reloadExecutor,
		CompletableFuture<Unit> initialStage,
		List<PackResources> packs,
		CallbackInfoReturnable<ReloadInstance> callbackInfo
	) {
		ReloadExecutionContext context = ReloadLifecycle.startReload();
		PACKFORGE_CONTEXT.set(context);
	}

	@Inject(method = "createReload", at = @At("RETURN"))
	private void packforge$finishReload(
		Executor preparationExecutor,
		Executor reloadExecutor,
		CompletableFuture<Unit> initialStage,
		List<PackResources> packs,
		CallbackInfoReturnable<ReloadInstance> callbackInfo
	) {
		ReloadExecutionContext context = PACKFORGE_CONTEXT.get();
		PACKFORGE_CONTEXT.remove();
		if (context == null) {
			return;
		}

		ReloadInstance instance = callbackInfo.getReturnValue();
		if (instance == null) {
			ReloadLifecycle.finishReload(context, new IllegalStateException("Minecraft returned no resource reload instance"));
			return;
		}

		ReloadableResourceManager manager = (ReloadableResourceManager) (Object) this;
		instance.done().whenComplete((result, error) -> {
			if (error == null && ReloadExecutionContext.isCurrent(context)) {
				RuntimeResourceHash.report(manager, context.reloadId());
			}
			ReloadLifecycle.finishReload(context, error);
		});
	}
}
