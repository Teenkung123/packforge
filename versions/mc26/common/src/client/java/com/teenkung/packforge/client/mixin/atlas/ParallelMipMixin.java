package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.atlas.SpriteMipPreparation;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Separate stage so diagnostic build variants can omit scheduling while retaining the solidify kernel. */
@Mixin(SpriteLoader.class)
public abstract class ParallelMipMixin {
    @WrapOperation(method = "stitch", at = @At(value = "INVOKE", target =
        "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> packforge$parallelMipFuture(Runnable command, Executor executor,
            Operation<CompletableFuture<Void>> original) {
        return SpriteMipPreparation.submit(command, executor, (task, target) -> original.call(task, target));
    }

    // Official 26.1.2 and 26.2 bytecode: this synthetic method contains only values().forEach(consumer).
    // A differently shaped platform version keeps the complete original runnable and logs its bypass.
    @WrapOperation(method = "lambda$stitch$2", at = @At(value = "INVOKE", target =
        "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V"), require = 0)
    private static void packforge$parallelMipLoop(Collection<TextureAtlasSprite> sprites,
            Consumer<TextureAtlasSprite> consumer, Operation<Void> original) {
        if (!SpriteMipPreparation.tryParallel(sprites, consumer)) original.call(sprites, consumer);
    }
}
