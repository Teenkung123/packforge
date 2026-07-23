package com.teenkung.packforge.client.mixin.ui;

import com.mojang.blaze3d.platform.Window;
import com.teenkung.packforge.client.compat.MinecraftGuiCompat;
import com.teenkung.packforge.client.ui.ReloadSummaryToast;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadStatus;
import com.teenkung.packforge.startup.StartupStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private ReloadInstance reload;
	@Shadow @Final private Consumer<Optional<Throwable>> onFinish;
	@Shadow private long fadeOutStart;

	@Invoker("isReadyToFadeOut")
	protected abstract boolean packforge$isReadyToFadeOut();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void packforge$skipFadeOut(CallbackInfo ci) {
		if (!FeatureFlags.loadingScreenFadeOutDisabled() || this.fadeOutStart != -1L || !this.reload.isDone() || !this.packforge$isReadyToFadeOut()) {
			return;
		}
		try {
			this.reload.checkExceptions();
			this.onFinish.accept(Optional.empty());
		} catch (Throwable t) {
			this.onFinish.accept(Optional.of(t));
		}
		var screen = MinecraftGuiCompat.screen(this.minecraft);
		if (screen != null) {
			Window window = this.minecraft.getWindow();
			screen.init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
		}
		MinecraftGuiCompat.setOverlay(this.minecraft, null);
		ReloadSummaryToast.showPending();
		ci.cancel();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void packforge$showReloadSummaryToast(CallbackInfo ci) {
		ReloadSummaryToast.showPending();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void packforge$drawReloadStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickProgress, CallbackInfo ci) {
		boolean drawReload = FeatureFlags.loadingStatusOverlayEnabled() && ReloadStatus.isActive();
		boolean drawStartup = FeatureFlags.startupStatusOverlayEnabled() && StartupStatus.isActive();
		if ((!drawReload && !drawStartup) || !ReloadStatus.isStatusTextReady()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int centerX = graphics.guiWidth() / 2;
		int barY = (int)((double)graphics.guiHeight() * 0.8325);
		int titleY = Math.max(8, barY - 29);
		if (drawStartup) {
			int startupY = Math.max(8, titleY - font.lineHeight - 14);
			graphics.centeredText(font, StartupStatus.line(), centerX, startupY, ARGB.white(215));
			graphics.centeredText(font, StartupStatus.detailLine(), centerX, startupY + font.lineHeight + 2, ARGB.white(170));
		}
		if (!drawReload) {
			return;
		}
		int detailY = titleY + font.lineHeight + 2;
		float progress = ReloadStatus.isComplete() ? 1.0f : this.reload.getActualProgress();
		graphics.centeredText(font, ReloadStatus.line(progress), centerX, titleY, ARGB.white(235));
		graphics.centeredText(font, ReloadStatus.detailLine(), centerX, detailY, ARGB.white(190));
	}
}
