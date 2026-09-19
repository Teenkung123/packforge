package dev.packbench.correctness.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.packbench.correctness.TextureCapture;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public abstract class ResourceGenerationMixin {
    @Shadow @Final private PackType type;

    @WrapMethod(method = "createReload")
    private ReloadInstance correctness$generation(Executor prepare, Executor apply, CompletableFuture<Unit> initial,
                                                  List<PackResources> packs, Operation<ReloadInstance> original) {
        if (!TextureCapture.ENABLED || type != PackType.CLIENT_RESOURCES) return original.call(prepare, apply, initial, packs);
        TextureCapture.Generation generation = TextureCapture.beginGeneration();
        TextureCapture.Context context = generation.context();
        try {
            ReloadInstance reload = TextureCapture.within(context,
                () -> original.call(TextureCapture.bind(context, prepare), TextureCapture.bind(context, apply), initial, packs));
            reload.done().whenComplete((ignored, failure) -> TextureCapture.completeGeneration(generation, failure));
            return reload;
        } catch (RuntimeException | Error failure) {
            TextureCapture.completeGeneration(generation, failure);
            throw failure;
        }
    }
}
