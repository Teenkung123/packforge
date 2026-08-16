package com.teenkung.packforge.client;

import java.util.concurrent.CompletableFuture;

/** Test-only seam for starting a reload without Minecraft's cached pending future. */
public interface RuntimeSmokeMinecraftBridge {
	CompletableFuture<Void> packforge$startRuntimeSmokeReload();
}
