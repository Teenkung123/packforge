package com.teenkung.packforge.client.ui;

import com.teenkung.packforge.client.compat.MinecraftGuiCompat;
import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class ReloadSummaryToast {
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId();
	private static final SystemToast.SystemToastId LIVE_TOAST_ID = new SystemToast.SystemToastId();
	private static LiveToast liveToast;
	private static long lastLiveUpdateNs;

	public static void pump() {
		if (!ReloadStatus.externalFeedbackEnabled() || !ReloadStatus.isStatusTextReady()
			|| !ReloadStatus.isActive()) {
			if (liveToast != null) {
				liveToast.forceHide();
				liveToast = null;
			}
			showPending();
			return;
		}
		long now = System.nanoTime();
		if (liveToast != null && now - lastLiveUpdateNs < 250_000_000L) return;
		lastLiveUpdateNs = now;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null) {
			if (liveToast == null) {
				liveToast = new LiveToast(minecraft.font);
				MinecraftGuiCompat.toastManager(minecraft).addToast(liveToast);
			} else {
				liveToast.refresh(minecraft.font);
			}
		}
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
		if (minecraft == null) {
			return;
		}
		String message = "Pack took " + summary.elapsedMs() + "ms to complete";
		if (!summary.success()) {
			message += " with errors";
		}
		SystemToast.addOrUpdate(
			MinecraftGuiCompat.toastManager(minecraft),
			TOAST_ID,
			Component.literal("PackForge reload"),
			Component.literal(message)
		);
	}

	/** Fixed geometry prevents long or changing mod listener names from expanding the HUD. */
	private static final class LiveToast extends SystemToast {
		private static final int WIDTH = 280;

		private LiveToast(Font font) {
			super(LIVE_TOAST_ID, clipped(font, ReloadStatus.line(0.0F)), clipped(font, ReloadStatus.detailLine()));
		}

		private void refresh(Font font) {
			reset(clipped(font, ReloadStatus.line(0.0F)), clipped(font, ReloadStatus.detailLine()));
		}

		@Override
		public int width() { return WIDTH; }

		private static Component clipped(Font font, String text) {
			int available = WIDTH - 30;
			if (font.width(text) <= available) return Component.literal(text);
			String suffix = "...";
			return Component.literal(font.plainSubstrByWidth(text, available - font.width(suffix)) + suffix);
		}
	}

	private ReloadSummaryToast() {}
}
