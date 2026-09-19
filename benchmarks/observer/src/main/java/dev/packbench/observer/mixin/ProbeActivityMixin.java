package dev.packbench.observer.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.packbench.observer.BenchmarkObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FramerateLimitTracker.class)
public abstract class ProbeActivityMixin {
    @Inject(method = "onInputReceived", at = @At("HEAD"))
    private void benchmark$activity(CallbackInfo callback) {
        BenchmarkObserver.inputActivityObserved();
    }
}
