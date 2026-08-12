package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Isolates the 1.21.6+ SystemToast API from the otherwise compatible loading overlay. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayToastMixin {
	@Shadow @Final private Minecraft minecraft;

	@Inject(method = "render", at = @At("TAIL"))
	private void packforge$showReloadSummary(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		ReloadStatus.ReloadSummary summary = ReloadStatus.consumeSummaryToast();
		if (summary == null) {
			return;
		}
		String message = "Pack took " + summary.elapsedMs() + "ms to complete" + (summary.success() ? "" : " with errors");
		SystemToast.addOrUpdate(this.minecraft.getToastManager(), new SystemToast.SystemToastId(),
			Component.literal("PackForge reload"), Component.literal(message));
	}
}
