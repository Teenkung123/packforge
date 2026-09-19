package dev.packbench.observer.mixin;

import dev.packbench.observer.BenchmarkObserver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
    @Inject(method = "handleKeybinds", at = @At("RETURN"))
    private void benchmark$worldInput(CallbackInfo callback) {
        BenchmarkObserver.worldInputDispatched();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void benchmark$input(CallbackInfo callback) {
        BenchmarkObserver.inputTick();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void benchmark$frame(boolean advanceGameTime, CallbackInfo callback) {
        BenchmarkObserver.afterFrame((Minecraft) (Object) this);
    }
}
