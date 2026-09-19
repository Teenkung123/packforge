package dev.packbench.observer.mixin;

import dev.packbench.observer.BenchmarkObserver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenRenderMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void benchmark$menu(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        BenchmarkObserver.menuRendered();
    }
}
