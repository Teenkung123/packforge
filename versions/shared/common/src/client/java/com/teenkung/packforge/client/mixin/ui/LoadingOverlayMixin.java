package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.client.ui.ReloadSummaryToast;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
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
	private void packforge$render(
		GuiGraphics graphics,
		int mouseX,
		int mouseY,
		float tickDelta,
		CallbackInfo ci
	) {
		if (FeatureFlags.loadingStatusOverlayEnabled()
			&& ReloadStatus.isActive()
			&& ReloadStatus.isStatusTextReady()) {
			int centerX = graphics.guiWidth() / 2;
			int titleY = Math.max(8, (int) (graphics.guiHeight() * 0.8325) - 29);
			graphics.drawCenteredString(
				this.minecraft.font,
				ReloadStatus.line(ReloadStatus.displayProgress(this.reload.getActualProgress())),
				centerX,
				titleY,
				0xEBFFFFFF
			);
			graphics.drawCenteredString(
				this.minecraft.font,
				ReloadStatus.detailLine(),
				centerX,
				titleY + this.minecraft.font.lineHeight + 2,
				0xBEFFFFFF
			);
		}
		ReloadSummaryToast.showPending();
	}

	@Redirect(
		method = "render",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;fadeOutStart:J",
			opcode = Opcodes.PUTFIELD
		)
	)
	private void packforge$skipFadeOnly(LoadingOverlay overlay, long value) {
		this.fadeOutStart = FeatureFlags.loadingScreenFadeOutDisabled() && value != -1L
			? value - LoadingOverlay.FADE_OUT_TIME
			: value;
	}
}
