package com.teenkung.packforge.client.mixin.model;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.model.ModelSourceDiagnostics;
import com.teenkung.packforge.concurrent.CoalescingExecutor;
import com.teenkung.packforge.concurrent.ModelSchedulingPlan;
import com.teenkung.packforge.platform.PackForgeCompat;
import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.Map;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

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
		return ModelSourceDiagnostics.observe(executor, observedExecutor -> {
			if (PackForgeCompat.mustPreservePlatformModelLoading()) {
				return original.call(manager, observedExecutor);
			}
			ModelSchedulingPlan plan = ModelSchedulingPlan.current();
			return switch (plan.strategy()) {
				case ORIGINAL -> original.call(manager, observedExecutor);
				// DIRECT_BATCHED is intentionally unreachable until hook safety is proven.
				case DIRECT_BATCHED -> original.call(manager, observedExecutor);
				case COALESCED_ORIGINAL -> original.call(
					manager, CoalescingExecutor.bounded(observedExecutor, plan.workerBudget()));
			};
		});
	}

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"))
	private static Map<Identifier, Resource> packforge$observeModelEnumeration(
		FileToIdConverter converter, ResourceManager manager, Operation<Map<Identifier, Resource>> original
	) {
		long start = ModelSourceDiagnostics.start();
		Map<Identifier, Resource> resources = original.call(converter, manager);
		ModelSourceDiagnostics.enumerated(resources.size(), start);
		return resources;
	}

	// 26.1-26.2 call CuboidModel directly; 26.3 routes through NeoForge's parser.
	// Exactly one stable parser call must remain present rather than silently losing diagnostics.
	@Group(name = "packforge$modelParser", min = 1, max = 1)
	@WrapOperation(method = "*", require = 0, at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/resources/model/cuboid/CuboidModel;fromStream(Ljava/io/Reader;)Lnet/minecraft/client/resources/model/cuboid/CuboidModel;"))
	private static CuboidModel packforge$observeOriginalModelParse(Reader reader, Operation<CuboidModel> original) {
		return ModelSourceDiagnostics.parse(reader, wrapped -> original.call(wrapped));
	}

	@Group(name = "packforge$modelParser", min = 1, max = 1)
	@WrapOperation(method = "*", require = 0, at = @At(value = "INVOKE", target =
		"Lnet/neoforged/neoforge/client/model/UnbakedModelParser;parse(Ljava/io/Reader;)Lnet/minecraft/client/resources/model/UnbakedModel;"))
	private static UnbakedModel packforge$observeNeoForgeModelParse(Reader reader, Operation<UnbakedModel> original) {
		return ModelSourceDiagnostics.parse(reader, wrapped -> original.call(wrapped));
	}
}
