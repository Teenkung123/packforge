package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.client.ui.ReloadSummaryToast;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before the fade-control cancellation path so a completed reload cannot lose its toast. */
@Mixin(value = LoadingOverlay.class, priority = 1100)
public abstract class LoadingOverlayToastMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void packforge$showReloadSummary(CallbackInfo ci) {
		ReloadSummaryToast.showPending();
	}
}
