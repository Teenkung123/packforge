package dev.packbench.correctness.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.packbench.correctness.TextureCapture;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
    @Shadow @Final private Identifier location;

    @WrapMethod(method = "loadAndStitch")
    private CompletableFuture<SpriteLoader.Preparations> correctness$atlas(ResourceManager resources, Identifier atlas,
            int mipLevel, Executor executor, Set<MetadataSectionType<?>> additional,
            Operation<CompletableFuture<SpriteLoader.Preparations>> original) {
        if (!TextureCapture.ENABLED) return original.call(resources, atlas, mipLevel, executor, additional);
        TextureCapture.Atlas capture = TextureCapture.beginAtlas(location.toString());
        if (capture == null) return original.call(resources, atlas, mipLevel, executor, additional);
        TextureCapture.Context context = capture.context();
        try {
            CompletableFuture<SpriteLoader.Preparations> result = TextureCapture.within(context,
                () -> original.call(resources, atlas, mipLevel, TextureCapture.bind(context, executor), additional));
            result.whenComplete((preparations, failure) -> {
                if (failure != null) TextureCapture.completeAtlas(capture, failure);
                else preparations.readyForUpload().whenComplete((ignored, mipFailure) -> TextureCapture.completeAtlas(capture, mipFailure));
            });
            // Preserve the actual future and cancellation behavior; never return an observation-dependent future.
            return result;
        } catch (RuntimeException | Error failure) {
            TextureCapture.completeAtlas(capture, failure);
            throw failure;
        }
    }
}
