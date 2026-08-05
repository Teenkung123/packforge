package com.teenkung.packforge.mixin.observe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadLifecycle;
import com.teenkung.packforge.loader.RuntimeResourceHash;
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
		ReloadableResourceManager manager = (ReloadableResourceManager) (Object) this;
		try {
			ReloadInstance instance = original.call(preparationExecutor, reloadExecutor, initialStage, packs);
			instance.done().whenComplete((result, error) -> {
				if (error == null && ReloadExecutionContext.isCurrent(context)) {
					RuntimeResourceHash.report(manager, context.reloadId());
				}
				ReloadLifecycle.finishReload(context, error);
			});
			return instance;
		} catch (RuntimeException | Error error) {
			ReloadLifecycle.finishReload(context, error);
			throw error;
		}
	}
}
