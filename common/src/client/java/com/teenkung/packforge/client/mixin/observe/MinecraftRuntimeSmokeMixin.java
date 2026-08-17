package com.teenkung.packforge.client.mixin.observe;

import com.teenkung.packforge.client.RuntimeSmokeMinecraftBridge;
import com.teenkung.packforge.client.RuntimeSmokeScenarioController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Scenario-only access to Minecraft's reload state. It is inert unless the
 * controller has armed the exact deterministic failure marker.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftRuntimeSmokeMixin implements RuntimeSmokeMinecraftBridge {
	@Shadow private CompletableFuture<Void> pendingReload;

	@Shadow public abstract void setOverlay(Overlay overlay);

	@Shadow public abstract CompletableFuture<Void> reloadResourcePacks();

	@Inject(method = "rollbackResourcePacks", at = @At("HEAD"), cancellable = true)
	private void packforge$suppressScenarioRollback(CallbackInfo callbackInfo) {
		if (!RuntimeSmokeScenarioController.shouldSuppressVanillaRollback()) {
			return;
		}
		this.pendingReload = null;
		this.setOverlay(null);
		RuntimeSmokeScenarioController.recordOverlayCleared();
		callbackInfo.cancel();
	}

	@Override
	public CompletableFuture<Void> packforge$startRuntimeSmokeReload() {
		this.pendingReload = null;
		this.setOverlay(null);
		RuntimeSmokeScenarioController.recordOverlayCleared();
		return this.reloadResourcePacks();
	}
}
