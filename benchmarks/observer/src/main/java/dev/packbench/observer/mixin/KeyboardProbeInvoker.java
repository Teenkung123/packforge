package dev.packbench.observer.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardProbeInvoker {
    @Invoker("charTyped")
    void benchmark$dispatchCharacter(long windowHandle, CharacterEvent event);
}
