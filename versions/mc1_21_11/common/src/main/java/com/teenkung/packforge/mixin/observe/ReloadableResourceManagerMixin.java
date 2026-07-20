package com.teenkung.packforge.mixin.observe;

import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.ReloadSessionTracker;
import com.teenkung.packforge.loader.RuntimeResourceHash;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@Inject(method = "createReload", at = @At("HEAD"))
	private void packforge$reloadStart(
		Executor preparationExecutor,
		Executor reloadExecutor,
		CompletableFuture<Unit> initialStage,
		List<PackResources> packs,
		CallbackInfoReturnable<ReloadInstance> cir
	) {
		ReloadSessionTracker.startReload();
		LoaderTimings.onReloadStart();
	}

	@Inject(method = "createReload", at = @At("RETURN"))
	private void packforge$trackReloadCompletion(
		Executor preparationExecutor,
		Executor reloadExecutor,
		CompletableFuture<Unit> initialStage,
		List<PackResources> packs,
		CallbackInfoReturnable<ReloadInstance> cir
	) {
		ReloadableResourceManager manager = (ReloadableResourceManager) (Object) this;
		long reloadId = ReloadSessionTracker.current().id();
		cir.getReturnValue().done().whenComplete((result, error) -> {
			LoaderTimings.onReloadEnd(error);
			LoaderTimings.onReloadComplete(error);
			if (error == null) {
				RuntimeResourceHash.report(manager, reloadId);
			}
		});
	}
}
