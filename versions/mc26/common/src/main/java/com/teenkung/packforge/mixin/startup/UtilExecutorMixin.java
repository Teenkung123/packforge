package com.teenkung.packforge.mixin.startup;

import com.teenkung.packforge.startup.StartupExecutorTuner;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ForkJoinWorkerThread;

@Mixin(Util.class)
public abstract class UtilExecutorMixin {
	@Redirect(
		method = "makeExecutor",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/Util;maxAllowedExecutorThreads()I"
		)
	)
	private static int packforge$tuneMaxThreads(String name) {
		return StartupExecutorTuner.tuneMaxThreads(name, Util.maxAllowedExecutorThreads());
	}

	@Redirect(
		method = "lambda$makeExecutor$0",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/concurrent/ForkJoinWorkerThread;setName(Ljava/lang/String;)V"
		)
	)
	private static void packforge$tuneThreadPriority(ForkJoinWorkerThread thread, String name) {
		StartupExecutorTuner.applyThreadSettings(thread, name);
	}
}
