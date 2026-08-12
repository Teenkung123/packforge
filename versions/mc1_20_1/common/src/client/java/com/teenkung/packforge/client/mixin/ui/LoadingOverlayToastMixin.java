package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.client.ui.ReloadSummaryToast;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the PackForge-only reload summary available when Quick Pack owns loading status. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayToastMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void packforge$showReloadSummary(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		ReloadSummaryToast.showPending();
	}
}
