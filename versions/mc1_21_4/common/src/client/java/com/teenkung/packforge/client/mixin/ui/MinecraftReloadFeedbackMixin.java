package com.teenkung.packforge.client.mixin.ui;

import com.teenkung.packforge.client.ui.ReloadSummaryToast;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftReloadFeedbackMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void packforge$pumpReloadFeedback(boolean renderLevel, CallbackInfo ci) {
		ReloadSummaryToast.pump();
	}
}
