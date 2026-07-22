package com.teenkung.packforge.client.mixin.model;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.model.ModelParseOptimizer;
import com.teenkung.packforge.platform.PackForgeCompat;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
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
	private CompletableFuture<Map<Identifier, UnbakedModel>> packforge$loadBlockModelsBatched(
		ResourceManager manager,
		Executor executor,
		Operation<CompletableFuture<Map<Identifier, UnbakedModel>>> original
	) {
		if (PackForgeCompat.mustPreservePlatformModelLoading()) {
			return original.call(manager, executor);
		}
		return ModelParseOptimizer.loadBlockModels(manager, executor);
	}
}
