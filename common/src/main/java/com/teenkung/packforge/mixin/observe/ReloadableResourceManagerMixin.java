package com.teenkung.packforge.mixin.observe;

import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.ReloadSessionTracker;
import com.teenkung.packforge.loader.ReloadStatus;
import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@Unique private long packforge$startupReloadStartNs;

	@Inject(method = "createReload", at = @At("HEAD"))
	private void packforge$reloadStart(CallbackInfoReturnable<ReloadInstance> cir) {
		this.packforge$startupReloadStartNs = System.nanoTime();
		ReloadSessionTracker.startReload();
		LoaderTimings.onReloadStart();
		ReloadStatus.start();
		if (StartupTimings.isActive()) {
			StartupStatus.update("Loading", "client resources");
			StartupTimings.event("resource_reload_start");
		}
	}

	@Inject(method = "createReload", at = @At("RETURN"))
	private void packforge$reloadEnd(CallbackInfoReturnable<ReloadInstance> cir) {
		LoaderTimings.onReloadEnd();
		cir.getReturnValue().done().whenComplete((result, error) -> {
			LoaderTimings.onReloadComplete(error);
			ReloadStatus.finish(error);
			if (StartupTimings.isActive()) {
				StartupStatus.update(error == null ? "Finishing" : "Failed", "client resources");
			}
			if (this.packforge$startupReloadStartNs != 0L && StartupTimings.isActive()) {
				StartupTimings.recordDuration("resource_reload_wall", System.nanoTime() - this.packforge$startupReloadStartNs);
				StartupTimings.event(error == null ? "resource_reload_complete" : "resource_reload_failed");
			}
		});
	}
}
