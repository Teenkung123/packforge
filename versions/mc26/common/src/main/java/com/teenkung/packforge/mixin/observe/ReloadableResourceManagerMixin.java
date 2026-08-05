package com.teenkung.packforge.mixin.observe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadLifecycle;
import com.teenkung.packforge.loader.RuntimeResourceHash;
import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@WrapMethod(method = "createReload")
	private ReloadInstance packforge$createReload(Operation<ReloadInstance> original) {
		ReloadExecutionContext context = ReloadLifecycle.startReload();
		long startupStartNs = System.nanoTime();
		if (context.features().startupStatusOverlayEnabled() && context.features().startupTimingActiveAtStart()) {
			StartupStatus.update("Loading", "client resources");
		}
		if (context.features().startupTimingActiveAtStart()) {
			StartupTimings.event("resource_reload_start");
		}
		ReloadableResourceManager manager = (ReloadableResourceManager) (Object) this;
		try {
			ReloadInstance instance = original.call();
			instance.done().whenComplete((result, error) -> {
				if (error == null && ReloadExecutionContext.isCurrent(context)) {
					RuntimeResourceHash.report(manager, context.reloadId());
				}
				ReloadLifecycle.finishReload(context, error);
				if (context.features().startupStatusOverlayEnabled() && context.features().startupTimingActiveAtStart()) {
					StartupStatus.update(error == null ? "Finishing" : "Failed", "client resources");
				}
				if (context.features().startupTimingActiveAtStart()) {
					StartupTimings.recordDuration("resource_reload_wall", System.nanoTime() - startupStartNs);
					StartupTimings.event(error == null ? "resource_reload_complete" : "resource_reload_failed");
				}
			});
			return instance;
		} catch (RuntimeException | Error error) {
			ReloadLifecycle.finishReload(context, error);
			if (context.features().startupTimingActiveAtStart()) {
				StartupTimings.recordDuration("resource_reload_wall", System.nanoTime() - startupStartNs);
				StartupTimings.event("resource_reload_failed");
			}
			throw error;
		}
	}
}
