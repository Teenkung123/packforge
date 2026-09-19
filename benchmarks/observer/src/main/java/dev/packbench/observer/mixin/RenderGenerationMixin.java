package dev.packbench.observer.mixin;

import dev.packbench.observer.BenchmarkObserver;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class RenderGenerationMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void benchmark$extract(DeltaTracker delta, boolean advance, CallbackInfo callback) {
        BenchmarkObserver.extractionStarted();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void benchmark$world(DeltaTracker delta, CallbackInfo callback) {
        BenchmarkObserver.worldRendered();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void benchmark$render(DeltaTracker delta, boolean advance, CallbackInfo callback) {
        BenchmarkObserver.renderCompleted();
    }
}
