package com.teenkung.packforge.client.ui;

import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

public final class ReloadSummaryToast implements Toast {
	private static final long DISPLAY_TIME_MS = 5_000L;
	private static final int LIVE_WIDTH = 280;
	private static LiveToast liveToast;
	private static long lastLiveUpdateNs;
	private final Component message;
	private Visibility visibility = Visibility.SHOW;
	private long firstSeen = -1L;

	private ReloadSummaryToast(ReloadStatus.ReloadSummary summary) {
		this.message = Component.literal(
			"Pack took " + summary.elapsedMs() + "ms to complete" + (summary.success() ? "" : " with errors")
		);
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
			minecraft.getToastManager().addToast(new ReloadSummaryToast(summary));
		}
	}

	/** Pumps completion feedback from the game loop when the loading overlay is displaced. */
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
			minecraft.getToastManager().addToast(liveToast);
		} else liveToast.refresh(minecraft.font);
	}

	private static final class LiveToast implements Toast {
		private Component line;
		private Component detail;
		private Visibility visibility = Visibility.SHOW;
		private LiveToast(Font font) { refresh(font); }
		private void refresh(Font font) { this.line = clipped(font, ReloadStatus.line(0.0F)); this.detail = clipped(font, ReloadStatus.detailLine()); this.visibility = Visibility.SHOW; }
		private void hide() { this.visibility = Visibility.HIDE; }
		@Override public int width() { return LIVE_WIDTH; }
		@Override public Visibility getWantedVisibility() { return this.visibility; }
		@Override public void update(ToastManager manager, long now) {}
		@Override public void render(GuiGraphics graphics, Font font, long now) {
			graphics.fill(0, 0, width(), height(), 0xE51C1C1C);
			graphics.drawString(font, this.line, 8, 7, 0xFFFFFFFF, false);
			graphics.drawString(font, this.detail, 8, 19, 0xFFE0E0E0, false);
		}
		private static Component clipped(Font font, String text) {
			int available = LIVE_WIDTH - 30;
			if (font.width(text) <= available) return Component.literal(text);
			String suffix = "...";
			return Component.literal(font.plainSubstrByWidth(text, available - font.width(suffix)) + suffix);
		}
	}

	@Override
	public Visibility getWantedVisibility() {
		return this.visibility;
	}

	@Override
	public void update(ToastManager manager, long now) {
		if (this.firstSeen < 0L) {
			this.firstSeen = now;
		}
		long displayTime = (long) (DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier());
		if (now - this.firstSeen >= displayTime) {
			this.visibility = Visibility.HIDE;
		}
	}

	@Override
	public void render(GuiGraphics graphics, Font font, long now) {
		graphics.fill(0, 0, width(), height(), 0xE51C1C1C);
		graphics.drawString(font, Component.literal("PackForge reload"), 8, 7, 0xFFFFFFFF, false);
		graphics.drawString(font, this.message, 8, 19, 0xFFE0E0E0, false);
	}
}
