package com.teenkung.packforge.client;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

/** Accesses the scenario-only Minecraft bridge without reflection. */
public final class RuntimeSmokeMinecraftCompat {
	public static CompletableFuture<Void> reloadForScenario(Minecraft minecraft) {
		if (minecraft instanceof RuntimeSmokeMinecraftBridge bridge) {
			return bridge.packforge$startRuntimeSmokeReload();
		}
		throw new IllegalStateException("PackForge runtime smoke Minecraft bridge is unavailable");
	}

	private RuntimeSmokeMinecraftCompat() {
	}
}
