package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** Keeps the original atlas runnable/consumer and extends readiness over its admitted mip workers. */
public final class SpriteMipPreparation {
    private static final ThreadLocal<SpriteMipPreparation> CURRENT = new ThreadLocal<>();
    private final ReloadExecutionContext context;
    private final AtlasLoadInvocation atlas;
    private final Executor executor;
    private final PreparationBudget.Reservation bookkeeping;
    private final CompletionFuture result = new CompletionFuture(this);
    private final CompletableFuture<Void> commandDone = new CompletableFuture<>();
    private final CompletableFuture<Void> sourceDone = new CompletableFuture<>();
    private final long submitted = System.nanoTime();
    private CompletableFuture<Void> source;
    private BoundedMipWork<?> work;
    private int phase; // 0: queued; 1: original runnable entered; 2: command done or prevented.
    private Throwable sourceFailure;
    private boolean cancellationRequested;
    private boolean finishing;
    private String bypass = "loop_hook_unavailable";

    private SpriteMipPreparation(ReloadExecutionContext context, AtlasLoadInvocation atlas,
            Executor executor, PreparationBudget.Reservation bookkeeping) {
        this.context = context;
        this.atlas = atlas;
        this.executor = executor;
        this.bookkeeping = bookkeeping;
        CompletableFuture.allOf(commandDone, sourceDone).whenComplete((ignored, failure) -> finishAfterCommand(failure));
    }

    public static CompletableFuture<Void> submit(Runnable command, Executor executor,
            BiFunction<Runnable, Executor, CompletableFuture<Void>> original) {
        ReloadExecutionContext context = ReloadExecutionContext.current();
        AtlasLoadInvocation atlas = AtlasLoadInvocation.current();
        if (context == null || atlas == null || atlas.resourcePackUnboundedOwner()
                || !context.features().atlasMipParallelEnabled()) return original.apply(command, executor);
        PreparationBudget.Reservation memory = context.preparationBudget().tryReserve(512);
        if (memory == null) return original.apply(command, executor);
        SpriteMipPreparation scope = new SpriteMipPreparation(context, atlas, executor, memory);
        try {
            CompletableFuture<Void> source = original.apply(() -> scope.runOriginal(command), executor);
            scope.attachSource(source);
            return scope.result;
        } catch (RuntimeException | Error failure) {
            scope.sourceFinished(failure);
            throw failure;
        }
    }

    /** Called only by the verified vanilla synthetic mip loop; its original Consumer is preserved. */
    public static boolean tryParallel(Collection<TextureAtlasSprite> sprites, Consumer<TextureAtlasSprite> action) {
        SpriteMipPreparation scope = CURRENT.get();
        if (scope == null) return false;
        return tryParallel(sprites, action, scope::eligible, SpriteMipPreparation::scratchBytes);
    }

    /** Shared composition for admitted owner types; ownership and scratch policy are supplied by the adapter. */
    static <T> boolean tryParallel(Collection<T> sprites, Consumer<T> action,
            Predicate<List<T>> eligible, ToLongFunction<T> scratchBytes) {
        SpriteMipPreparation scope = CURRENT.get();
        if (scope == null) return false;
        if (scope.cancellationRequested()) return true;
        synchronized (scope) {
            if (scope.work != null) return false;
        }
        scope.bypass = "capacity_or_font_priority";
        BoundedMipWork<T> prepared = BoundedMipWork.prepare(sprites, scope.executor,
            scope.context.features().workerBudget(), scope.context.preparationBudget(), scope.context::fontPreparationPending,
            eligible, scratchBytes, action, scope::observeWorker);
        if (prepared == null) return false;
        synchronized (scope) { scope.work = prepared; }
        if (scope.cancellationRequested()) prepared.future().cancel(false);
        prepared.run(); // Baseline drains inline. Never joins callbacks queued to this same executor.
        return true;
    }

    private boolean eligible(List<TextureAtlasSprite> sprites) {
        IdentityHashMap<SpriteContents, Boolean> owners = new IdentityHashMap<>();
        IdentityHashMap<NativeImage, Boolean> images = new IdentityHashMap<>();
        for (TextureAtlasSprite sprite : sprites) {
            if (sprite == null || sprite.getClass() != TextureAtlasSprite.class) { bypass = "custom_atlas_sprite"; return false; }
            SpriteContents contents = sprite.contents();
            if (contents == null || contents.getClass() != SpriteContents.class || !(contents instanceof MipSpriteAccess access)) {
                bypass = "custom_sprite_contents"; return false;
            }
            NativeImage[] levels = access.packforge$mipImages();
            if (levels == null || levels.length != 1 || levels[0] == null || levels[0].getClass() != NativeImage.class
                    || levels[0].format() != NativeImage.Format.RGBA) { bypass = "nonfresh_or_custom_mip_images"; return false; }
            if (owners.put(contents, Boolean.TRUE) != null || images.put(levels[0], Boolean.TRUE) != null) {
                bypass = "shared_sprite_or_image"; return false;
            }
        }
        return true;
    }

    private static long scratchBytes(TextureAtlasSprite sprite) {
        NativeImage image = ((MipSpriteAccess) sprite.contents()).packforge$mipImages()[0];
        // Additional workers cover vanilla's two pixel arrays plus both live queue buffers during
        // a resize. Kernel reservations remain independently accounted; retained mip outputs are
        // Minecraft's required atlas data and never wait for cache/budget admission.
        return Math.addExact(4096L, Math.multiplyExact(24L, (long) image.getWidth() * image.getHeight()));
    }

    private void observeWorker(Runnable action) {
        AtlasDiagnostics diagnostics = atlas.diagnostics();
        if (diagnostics == null) { action.run(); return; }
        AtlasDiagnostics.Sample sample = AtlasDiagnostics.start();
        Throwable failure = null;
        try { action.run(); }
        catch (RuntimeException | Error error) { failure = error; throw error; }
        finally { diagnostics.stage("mip_parallel_worker", sample, failure); }
    }

    private void runOriginal(Runnable command) {
        synchronized (this) {
            if (phase != 0) return;
            phase = 1;
        }
        SpriteMipPreparation previous = CURRENT.get();
        CURRENT.set(this);
        Throwable failure = null;
        try { if (!cancellationRequested()) command.run(); }
        catch (RuntimeException | Error error) { failure = error; throw error; }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            synchronized (this) {
                phase = 2;
                if (failure == null) failure = sourceFailure;
            }
            if (failure == null) commandDone.complete(null);
            else commandDone.completeExceptionally(failure);
        }
    }

    private void attachSource(CompletableFuture<Void> source) {
        synchronized (this) { this.source = source; }
        source.whenComplete((ignored, failure) -> sourceFinished(failure));
        if (cancellationRequested()) cancelSources();
    }

    private void sourceFinished(Throwable failure) {
        boolean prevented;
        BoundedMipWork<?> activeWork;
        synchronized (this) {
            if (failure != null) sourceFailure = failure;
            prevented = phase == 0;
            if (prevented) phase = 2;
            activeWork = work;
        }
        if (failure instanceof CancellationException && activeWork != null) activeWork.future().cancel(false);
        if (prevented) commandDone.completeExceptionally(failure == null
            ? new IllegalStateException("Mip executor completed without running its command") : failure);
        if (failure == null) sourceDone.complete(null);
        else sourceDone.completeExceptionally(failure);
    }

    private synchronized boolean cancellationRequested() {
        return cancellationRequested || sourceFailure instanceof CancellationException;
    }

    private boolean requestCancellation() {
        synchronized (this) {
            if (result.isDone() || finishing) return false;
            cancellationRequested = true;
        }
        cancelSources();
        // Never acknowledge successful Future.cancel while the returned readiness future is
        // incomplete. A queued command can become safely terminal synchronously here; active
        // work instead drains cooperatively and publishes its cancellation later.
        return result.isCancelled();
    }

    private void cancelSources() {
        CompletableFuture<Void> submittedSource;
        BoundedMipWork<?> activeWork;
        boolean cancelQueuedSource;
        synchronized (this) {
            submittedSource = source;
            activeWork = work;
            cancelQueuedSource = phase == 0;
        }
        if (activeWork != null) activeWork.future().cancel(false);
        // Cancelling a running source future could trigger another owner's cleanup callback
        // before its runnable returns. Stop our optional claims, then let that source finish.
        if (submittedSource != null && cancelQueuedSource) submittedSource.cancel(false);
    }

    private void finishAfterCommand(Throwable commandFailure) {
        BoundedMipWork<?> completedWork;
        synchronized (this) { completedWork = work; }
        CompletableFuture<Void> drained = completedWork == null ? CompletableFuture.completedFuture(null) : completedWork.drained();
        drained.whenComplete((ignored, drainFailure) -> {
            CompletableFuture<Void> operation = completedWork == null ? CompletableFuture.completedFuture(null) : completedWork.future();
            operation.whenComplete((unused, workFailure) -> {
                bookkeeping.close();
                Throwable failure;
                synchronized (SpriteMipPreparation.this) {
                    finishing = true;
                    failure = sourceFailure != null ? sourceFailure : commandFailure != null ? commandFailure : workFailure;
                    if (cancellationRequested && !(failure instanceof CancellationException)) {
                        CancellationException cancelled = new CancellationException("Mip preparation cancelled after active work drained");
                        if (failure != null) cancelled.addSuppressed(failure);
                        failure = cancelled;
                    }
                }
                if (atlas.diagnostics() != null) {
                    atlas.diagnostics().wallStage("mip_parallel_ready", submitted, failure);
                    PackForge.LOGGER.info("PackForge mip preparation: reload={} atlas={} workers={} sprites={} rejectedExtras={} reason={}",
                        context.reloadId(), atlas.atlas(), completedWork == null ? 1 : completedWork.startedWorkers(),
                        completedWork == null ? 0 : completedWork.worked(), completedWork == null ? 0 : completedWork.rejectedWorkers(),
                        completedWork == null ? bypass : completedWork.startedWorkers() > 1 ? "admitted" : "baseline_only");
                }
                result.detach();
                if (failure == null) result.complete(null);
                else result.completeExceptionally(failure);
                synchronized (SpriteMipPreparation.this) { source = null; work = null; }
            });
        });
    }

    /**
     * Readiness also gates Minecraft's resource cleanup. While work is active, cancel returns
     * false and records a cooperative stop request; terminal cancellation is published only
     * after the command, source, and running consumers have drained. A true return always
     * means isDone() and isCancelled() are already true. Native pixel work is never interrupted.
     */
    private static final class CompletionFuture extends CompletableFuture<Void> {
        private volatile SpriteMipPreparation owner;
        private CompletionFuture(SpriteMipPreparation owner) { this.owner = owner; }
        private void detach() { owner = null; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            SpriteMipPreparation current = owner;
            return current != null && current.requestCancellation();
        }
    }
}
