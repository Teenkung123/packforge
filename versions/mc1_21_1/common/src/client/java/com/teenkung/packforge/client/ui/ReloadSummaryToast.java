package com.teenkung.packforge.client.ui;

import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

public final class ReloadSummaryToast implements Toast {
	private static final long DISPLAY_TIME_MS = 5_000L;
	private final Component message;

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
			minecraft.getToasts().addToast(new ReloadSummaryToast(summary));
		}
	}

	@Override
	public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceVisible) {
		graphics.fill(0, 0, width(), height(), 0xE51C1C1C);
		graphics.drawString(component.getMinecraft().font, Component.literal("PackForge reload"), 8, 7, 0xFFFFFFFF, false);
		graphics.drawString(component.getMinecraft().font, this.message, 8, 19, 0xFFE0E0E0, false);
		long displayTime = (long) (DISPLAY_TIME_MS * component.getNotificationDisplayTimeMultiplier());
		return timeSinceVisible < displayTime ? Visibility.SHOW : Visibility.HIDE;
	}
}
