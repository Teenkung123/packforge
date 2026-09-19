package dev.packbench.observer.mixin;

import dev.packbench.observer.BenchmarkObserver;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardDispatchMixin {
    @Inject(method = "charTyped", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z",
        shift = At.Shift.AFTER))
    private void benchmark$dispatched(long handle, CharacterEvent event, CallbackInfo callback) {
        BenchmarkObserver.menuInputDispatched(event);
    }
}
