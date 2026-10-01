package com.teenkung.packforge.client.mixin.observe;

import com.teenkung.packforge.loader.ReloadTrace;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayTraceMixin {
	@Shadow @Final private ReloadInstance reload;
	@Shadow private long fadeOutStart;

	@Inject(method = "tick", at = @At("HEAD"))
	private void packforge$traceReload(CallbackInfo ci) {
		if (ReloadTrace.isEnabled()) ReloadTrace.overlay(this, this.reload, this.fadeOutStart, this.reload.isDone(), this.reload.getActualProgress());
	}
}
