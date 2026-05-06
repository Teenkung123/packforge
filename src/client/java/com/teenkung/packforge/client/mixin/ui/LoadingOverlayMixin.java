package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow @Final private ReloadInstance reload;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void packforge$drawReloadStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickProgress, CallbackInfo ci) {
		if (!FeatureFlags.loadingStatusOverlayEnabled() || !ReloadStatus.isActive()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int centerX = graphics.guiWidth() / 2;
		int barY = (int)((double)graphics.guiHeight() * 0.8325);
		int titleY = Math.max(8, barY - 29);
		int detailY = titleY + font.lineHeight + 2;
		float progress = ReloadStatus.isComplete() ? 1.0f : this.reload.getActualProgress();
		graphics.centeredText(font, ReloadStatus.line(progress), centerX, titleY, ARGB.white(235));
		graphics.centeredText(font, ReloadStatus.detailLine(), centerX, detailY, ARGB.white(190));
	}
}
