package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private ReloadInstance reload;
	@Shadow private long fadeOutStart;

	@Inject(method = "render", at = @At("TAIL"))
	private void packforge$drawStatus(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		ReloadStatus.ReloadSummary summary = ReloadStatus.consumeSummaryToast();
		if (summary != null) {
			String message = "Pack took " + summary.elapsedMs() + "ms to complete" + (summary.success() ? "" : " with errors");
			SystemToast.addOrUpdate(this.minecraft.getToastManager(), new SystemToast.SystemToastId(), Component.literal("PackForge reload"), Component.literal(message));
		}
		if (!ReloadStatus.isStatusTextReady() || !FeatureFlags.loadingStatusOverlayEnabled() || !ReloadStatus.isActive()) return;
		Font font = this.minecraft.font;
		int x = graphics.guiWidth() / 2;
		int y = Math.max(8, (int) (graphics.guiHeight() * 0.8325) - 29);
		float progress = ReloadStatus.displayProgress(this.reload.getActualProgress());
		graphics.drawCenteredString(font, ReloadStatus.line(progress), x, y, 0xEBFFFFFF);
		graphics.drawCenteredString(font, ReloadStatus.detailLine(), x, y + font.lineHeight + 2, 0xBEFFFFFF);
	}

	@Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;fadeOutStart:J", opcode = Opcodes.PUTFIELD))
	private void packforge$skipFadeOnly(LoadingOverlay overlay, long value) {
		this.fadeOutStart = FeatureFlags.loadingScreenFadeOutDisabled() && value != -1L ? value - LoadingOverlay.FADE_OUT_TIME : value;
	}
}
