package dev.packbench.observer.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.packbench.observer.BenchmarkObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowFocusMixin {
    @Inject(method = "onFocus", at = @At("TAIL"))
    private void benchmark$focus(long handle, boolean focused, CallbackInfo callback) {
        if (((Window) (Object) this).handle() == handle) BenchmarkObserver.onWindowFocus(focused);
    }

    @Inject(method = "onFramebufferResize", at = @At("TAIL"))
    private void benchmark$framebufferSize(long handle, int width, int height, CallbackInfo callback) {
        Window window = (Window) (Object) this;
        if (window.handle() == handle) BenchmarkObserver.onFramebufferSize(window.getWidth(), window.getHeight());
    }
}
