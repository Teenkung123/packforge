package com.teenkung.packforge.client.mixin.startup;

import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class MainStartupMixin {
	@Inject(method = "main", at = @At("HEAD"))
	private static void packforge$mainStart(String[] args, CallbackInfo ci) {
		StartupTimings.reset();
		StartupStatus.start();
		StartupStatus.update("Starting", "Minecraft client entrypoint");
		StartupTimings.event("client_main_start");
	}
}
