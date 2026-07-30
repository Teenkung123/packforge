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
	private final Component message;
	private Visibility visibility = Visibility.SHOW;
	private long firstSeen = -1L;

	private ReloadSummaryToast(ReloadStatus.ReloadSummary summary) {
		this.message = Component.literal(
			"Pack took " + summary.elapsedMs() + "ms to complete" + (summary.success() ? "" : " with errors")
		);
	}

	public static void showPending() {
		ReloadStatus.ReloadSummary summary = ReloadStatus.consumeSummaryToast();
		if (summary == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null) {
			minecraft.getToastManager().addToast(new ReloadSummaryToast(summary));
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
