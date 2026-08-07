package com.teenkung.packforge.client.mixin.model;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.concurrent.CoalescingExecutor;
import com.teenkung.packforge.concurrent.ModelSchedulingPlan;
import com.teenkung.packforge.platform.PackForgeCompat;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ModelManager.class)
public abstract class ModelManagerMixin {
	@WrapOperation(
		method = "reload",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockModels(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private CompletableFuture<Map<ResourceLocation, BlockModel>> packforge$batchModels(
		ResourceManager manager,
		Executor executor,
		Operation<CompletableFuture<Map<ResourceLocation, BlockModel>>> original
	) {
		if (PackForgeCompat.mustPreservePlatformModelLoading()) {
			return original.call(manager, executor);
		}
		ModelSchedulingPlan plan = ModelSchedulingPlan.current();
		return switch (plan.strategy()) {
			case ORIGINAL -> original.call(manager, executor);
			// DIRECT_BATCHED is intentionally unreachable until hook safety is proven.
			case DIRECT_BATCHED -> original.call(manager, executor);
			case COALESCED_ORIGINAL -> original.call(
				manager,
				CoalescingExecutor.bounded(executor, plan.workerBudget())
			);
		};
	}
}
