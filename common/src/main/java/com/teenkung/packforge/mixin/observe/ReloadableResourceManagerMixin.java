package com.teenkung.packforge.mixin.observe;

import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@Inject(method = "createReload", at = @At("HEAD"))
	private void packforge$reloadStart(CallbackInfoReturnable<ReloadInstance> cir) {
		LoaderTimings.onReloadStart();
		ReloadStatus.start();
	}

	@Inject(method = "createReload", at = @At("RETURN"))
	private void packforge$reloadEnd(CallbackInfoReturnable<ReloadInstance> cir) {
		LoaderTimings.onReloadEnd();
		cir.getReturnValue().done().whenComplete((result, error) -> {
			LoaderTimings.onReloadComplete(error);
			ReloadStatus.finish(error);
		});
	}
}
