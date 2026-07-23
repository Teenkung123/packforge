package com.teenkung.packforge.client.ui;

import com.teenkung.packforge.client.compat.MinecraftGuiCompat;
import com.teenkung.packforge.loader.ReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class ReloadSummaryToast {
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId();

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

	private ReloadSummaryToast() {}
}
