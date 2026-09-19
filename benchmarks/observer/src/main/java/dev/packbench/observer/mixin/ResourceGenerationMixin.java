package dev.packbench.observer.mixin;

import dev.packbench.observer.BenchmarkObserver;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public abstract class ResourceGenerationMixin {
    @Shadow @Final private PackType type;
    @Unique private final ThreadLocal<ArrayDeque<Long>> benchmark$generations = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "createReload", at = @At("HEAD"))
    private void benchmark$begin(Executor prepare, Executor apply, CompletableFuture<Unit> initial,
                                 List<PackResources> packs, CallbackInfoReturnable<ReloadInstance> callback) {
        if (type == PackType.CLIENT_RESOURCES) benchmark$generations.get().push(BenchmarkObserver.resourceReloadStarted());
    }

    @Inject(method = "createReload", at = @At("RETURN"))
    private void benchmark$end(Executor prepare, Executor apply, CompletableFuture<Unit> initial,
                               List<PackResources> packs, CallbackInfoReturnable<ReloadInstance> callback) {
        if (type != PackType.CLIENT_RESOURCES) return;
        ArrayDeque<Long> generations = benchmark$generations.get();
        long generation = generations.pop();
        if (generations.isEmpty()) benchmark$generations.remove();
        callback.getReturnValue().done().whenComplete((ignored, failure) ->
            BenchmarkObserver.resourceReloadCompleted(generation, failure));
    }
}
