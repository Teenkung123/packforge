package com.teenkung.packforge.client.ui;

import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

public final class ReloadSummaryToast implements Toast {
	private static final long DISPLAY_TIME_MS = 5_000L;
	private static final int LIVE_WIDTH = 280;
	private static LiveToast liveToast;
	private static long lastLiveUpdateNs;
	private final Component message;

	private ReloadSummaryToast(ReloadStatus.ReloadSummary summary) {
		String text = "Pack took " + summary.elapsedMs() + "ms to complete";
		if (!summary.success()) {
			text += " with errors";
		}
		this.message = Component.literal(text);
	}

	public static void showPending() {
		if (!ReloadStatus.isStatusTextReady()) {
			return;
		}
		ReloadStatus.ReloadSummary summary = ReloadStatus.consumeSummaryToast();
		if (summary == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null) {
			minecraft.getToasts().addToast(new ReloadSummaryToast(summary));
		}
	}

	public static void pump() {
		if (!ReloadStatus.externalFeedbackEnabled() || !ReloadStatus.isStatusTextReady()
			|| !ReloadStatus.isActive()) {
			if (liveToast != null) liveToast.hide();
			liveToast = null;
			showPending();
			return;
		}
		long now = System.nanoTime();
		if (liveToast != null && now - lastLiveUpdateNs < 250_000_000L) return;
		lastLiveUpdateNs = now;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) return;
		if (liveToast == null) {
			liveToast = new LiveToast(minecraft.font);
			minecraft.getToasts().addToast(liveToast);
		} else {
			liveToast.refresh(minecraft.font);
		}
	}

	private static final class LiveToast implements Toast {
		private Component line;
		private Component detail;
		private boolean hidden;

		private LiveToast(Font font) { refresh(font); }

		private void refresh(Font font) {
			this.line = clipped(font, ReloadStatus.line(0.0F));
			this.detail = clipped(font, ReloadStatus.detailLine());
			this.hidden = false;
		}

		private void hide() { this.hidden = true; }

		@Override
		public int width() { return LIVE_WIDTH; }

		@Override
		public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceVisible) {
			if (this.hidden || liveToast != this || !ReloadStatus.externalFeedbackEnabled() || !ReloadStatus.isActive()) {
				return Visibility.HIDE;
			}
			Font font = component.getMinecraft().font;
			graphics.fill(0, 0, width(), height(), 0xE51C1C1C);
			graphics.drawString(font, this.line, 8, 7, 0xFFFFFFFF, false);
			graphics.drawString(font, this.detail, 8, 19, 0xFFE0E0E0, false);
			return Visibility.SHOW;
		}

		private static Component clipped(Font font, String text) {
			int available = LIVE_WIDTH - 30;
			if (font.width(text) <= available) return Component.literal(text);
			String suffix = "...";
			return Component.literal(font.plainSubstrByWidth(text, available - font.width(suffix)) + suffix);
		}
	}

	@Override
	public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceVisible) {
		graphics.fill(0, 0, width(), height(), 0xE51C1C1C);
		graphics.drawString(component.getMinecraft().font, Component.literal("PackForge reload"), 8, 7, 0xFFFFFFFF, false);
		graphics.drawString(component.getMinecraft().font, this.message, 8, 19, 0xFFE0E0E0, false);
		double multiplier = component.getNotificationDisplayTimeMultiplier();
		return timeSinceVisible < (long)(DISPLAY_TIME_MS * multiplier) ? Visibility.SHOW : Visibility.HIDE;
	}
}
