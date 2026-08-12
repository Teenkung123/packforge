package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.startup.StartupStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Startup progress is PackForge-only and intentionally independent of reload status ownership. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayStartupStatusMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void packforge$drawStartupStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickProgress, CallbackInfo ci) {
		if (!FeatureFlags.startupStatusOverlayEnabled() || !StartupStatus.isActive()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int centerX = graphics.guiWidth() / 2;
		int barY = (int)((double)graphics.guiHeight() * 0.8325);
		int titleY = Math.max(8, barY - 29);
		int startupY = Math.max(8, titleY - font.lineHeight - 14);
		graphics.centeredText(font, StartupStatus.line(), centerX, startupY, ARGB.white(215));
		graphics.centeredText(font, StartupStatus.detailLine(), centerX, startupY + font.lineHeight + 2, ARGB.white(170));
	}
}
