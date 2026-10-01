package com.teenkung.packforge.mixin.observe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadLifecycle;
import com.teenkung.packforge.loader.ReloadTrace;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@WrapMethod(method = "createReload")
	private ReloadInstance packforge$createReload(
		Executor preparationExecutor,
		Executor reloadExecutor,
		CompletableFuture<Unit> initialStage,
		List<PackResources> packs,
		Operation<ReloadInstance> original
	) {
		ReloadExecutionContext context = ReloadLifecycle.startReload();
		try {
			if (ReloadTrace.isEnabled()) ReloadTrace.started(context.reloadId(), packs.stream()
				.map(pack -> new ReloadTrace.PackIdentity(pack.packId(), pack.getClass().getName())).toList());
			ReloadInstance instance;
			try (ReloadExecutionContext.Scope ignored = ReloadExecutionContext.bind(context)) {
				instance = original.call(ReloadExecutionContext.bindPreparationExecutor(context, preparationExecutor),
					reloadExecutor, initialStage, packs);
			}
			ReloadTrace.created(context.reloadId(), instance);
			instance.done().whenComplete((result, error) -> {
				ReloadTrace.completed(context.reloadId(), instance, error);
				ReloadLifecycle.finishReload(context, error);
			});
			return instance;
		} catch (RuntimeException | Error error) {
			ReloadTrace.failedCreation(context.reloadId(), error);
			ReloadLifecycle.finishReload(context, error);
			throw error;
		}
	}
}
