package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.atlas.AtlasReport;
import com.teenkung.packforge.client.atlas.AtlasDiagnostics;
import com.teenkung.packforge.client.atlas.AtlasLoadInvocation;
import com.teenkung.packforge.client.atlas.AtlasRetry;
import com.teenkung.packforge.client.atlas.BoundedSpriteDecode;
import com.teenkung.packforge.client.atlas.CappedSpriteResourceLoader;
import com.teenkung.packforge.client.atlas.SpriteMetadataCache;
import com.teenkung.packforge.client.compat.ResourcePackUnboundedBridge;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Narrow mc26 sprite hooks; vanilla load/stitch control flow remains authoritative. */
@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@Shadow @Final private Identifier location;

	@WrapOperation(
		method = "loadAndStitch",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/texture/atlas/SpriteResourceLoader;create(Ljava/util/Set;)Lnet/minecraft/client/renderer/texture/atlas/SpriteResourceLoader;"
		)
	)
	private SpriteResourceLoader packforge$wrapLoader(
		Set<MetadataSectionType<?>> additional,
		Operation<SpriteResourceLoader> original
	) {
		SpriteResourceLoader vanilla = original.call(additional);
		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (packforge$resourcePackUnboundedOwnsAtlas()) {
			return vanilla;
		}
		if (plan.atlasCapApplies(this.location.toString())) {
			AtlasRetry.logCapUnavailable(this.location);
		}
		if (!plan.atlasRetryApplies(this.location.toString())) {
			return vanilla;
		}
		AtlasLoadInvocation invocation = AtlasLoadInvocation.current();
		if (invocation == null || invocation.state() != null) {
			AtlasRetry.logRetryUnavailable(this.location);
			return vanilla;
		}
		SpriteMetadataCache.AtlasState state = SpriteMetadataCache.bind(this.location, plan);
		invocation.bindState(state);
		return CappedSpriteResourceLoader.wrap(vanilla, state);
	}

	@WrapMethod(method = "loadAndStitch")
	private CompletableFuture<SpriteLoader.Preparations> packforge$associateAtlasState(
		ResourceManager resourceManager,
		Identifier atlasId,
		int mipLevel,
		Executor executor,
		Set<MetadataSectionType<?>> additional,
		Operation<CompletableFuture<SpriteLoader.Preparations>> original
	) {
		Identifier atlas = this.location;
		AtlasLoadInvocation invocation = new AtlasLoadInvocation(
			atlas,
			ResourcePackUnboundedBridge.configuredOwner(atlas)
		);
		try {
			CompletableFuture<SpriteLoader.Preparations> future = invocation.call(() ->
				original.call(resourceManager, atlasId, mipLevel, invocation.bind(executor), additional));
			SpriteMetadataCache.AtlasState state = invocation.state();
			AtlasDiagnostics diagnostics = invocation.diagnostics();
			if (future == null) {
				SpriteMetadataCache.fail(state, null);
				if (diagnostics != null) diagnostics.close();
				return null;
			}
			future.whenComplete((preparations, error) -> {
				if (error != null) {
					SpriteMetadataCache.fail(state, null);
					if (diagnostics != null) diagnostics.close();
				} else if (state != null) {
					AtlasReport.logAtlas(atlas, state);
					SpriteMetadataCache.finish(state);
				}
				if (error == null && diagnostics != null) {
					preparations.readyForUpload().whenComplete((ignored, mipError) -> diagnostics.close());
				}
			});
			return future;
		} catch (RuntimeException | Error failure) {
			SpriteMetadataCache.fail(invocation.state(), null);
			if (invocation.diagnostics() != null) invocation.diagnostics().close();
			throw failure;
		}
	}

	@WrapOperation(method = "loadAndStitch", at = @At(value = "INVOKE", target =
		"Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<List<SpriteSource.Loader>> packforge$timeSources(
		Supplier<List<SpriteSource.Loader>> supplier, Executor executor,
		Operation<CompletableFuture<List<SpriteSource.Loader>>> original
	) {
		AtlasLoadInvocation invocation = AtlasLoadInvocation.current();
		AtlasDiagnostics diagnostics = invocation == null ? null : invocation.diagnostics();
		if (diagnostics == null) return original.call(supplier, executor);
		return original.call((Supplier<List<SpriteSource.Loader>>) () -> {
			AtlasDiagnostics.Sample sample = AtlasDiagnostics.start();
			Throwable failure = null;
			try { return supplier.get(); }
			catch (RuntimeException | Error error) { failure = error; throw error; }
			finally { diagnostics.stage("source_enumeration", sample, failure); }
		}, executor);
	}

	@WrapOperation(method = "stitch", at = @At(value = "INVOKE", target =
		"Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<Void> packforge$timeMipChain(Runnable command, Executor executor, Operation<CompletableFuture<Void>> original) {
		AtlasLoadInvocation invocation = AtlasLoadInvocation.current();
		AtlasDiagnostics diagnostics = invocation == null ? null : invocation.diagnostics();
		if (diagnostics == null) return original.call(command, executor);
		long submitted = System.nanoTime();
		CompletableFuture<Void> future = original.call((Runnable) () -> {
			AtlasDiagnostics.Sample sample = AtlasDiagnostics.start();
			Throwable failure = null;
			try { command.run(); }
			catch (RuntimeException | Error error) { failure = error; throw error; }
			finally { diagnostics.stage("mip_chain_work", sample, failure); }
		}, executor);
		future.whenComplete((ignored, error) -> diagnostics.wallStage("mip_chain_ready", submitted, error));
		return future;
	}

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations packforge$retryOriginalStitch(
		List<SpriteContents> sprites,
		int mipLevel,
		Executor executor,
		Operation<SpriteLoader.Preparations> original
	) {
		AtlasLoadInvocation invocation = AtlasLoadInvocation.current();
		AtlasDiagnostics diagnostics = invocation == null ? null : invocation.diagnostics();
		AtlasDiagnostics.Sample sample = diagnostics == null ? null : AtlasDiagnostics.start();
		Throwable failure = null;
		try {
			SpriteMetadataCache.AtlasState state = SpriteMetadataCache.findState(this.location, sprites);
			if (state == null) return original.call(sprites, mipLevel, executor);
			return AtlasRetry.stitch(this.location, sprites, mipLevel, executor,
				(originalSprites, originalMipLevel, originalExecutor) -> original.call(originalSprites, originalMipLevel, originalExecutor), state);
		} catch (RuntimeException | Error error) {
			failure = error;
			throw error;
		} finally {
			if (diagnostics != null) diagnostics.stage("stitch", sample, failure);
		}
	}

	@WrapMethod(method = "runSpriteSuppliers")
	private static CompletableFuture<List<SpriteContents>> packforge$decodeBounded(
		SpriteResourceLoader resourceLoader,
		List<SpriteSource.Loader> loaders,
		Executor executor,
		Operation<CompletableFuture<List<SpriteContents>>> original
	) {
		AtlasLoadInvocation invocation = AtlasLoadInvocation.current();
		if (invocation != null && invocation.resourcePackUnboundedOwner()) {
			return original.call(resourceLoader, loaders, executor);
		}

		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.decodeEnabled() && !plan.phaseTimingsEnabled()) {
			return original.call(resourceLoader, loaders, executor);
		}

		AtlasDiagnostics diagnostics = invocation == null ? null : invocation.diagnostics();
		long startNs = diagnostics == null ? 0 : System.nanoTime();
		SpriteResourceLoader observedLoader = diagnostics == null ? resourceLoader : diagnostics.wrap(resourceLoader);
		List<SpriteSource.Loader> observedSuppliers = diagnostics == null ? loaders : diagnostics.observeLoaders(loaders);
		CompletableFuture<List<SpriteContents>> future = plan.decodeEnabled()
			? BoundedSpriteDecode.decode(observedSuppliers, executor, plan, loader -> loader.get(observedLoader))
			: original.call(observedLoader, observedSuppliers, executor);
		if (diagnostics != null) {
			future.whenComplete((ignored, error) -> diagnostics.decodeFinished(startNs, error));
		}
		// Return the owned decode future: cancelling a dependent timing future
		// would not cancel decoding or dispose images produced after cancellation.
		return future;
	}

	private boolean packforge$resourcePackUnboundedOwnsAtlas() {
		return ResourcePackUnboundedBridge.configuredOwner(this.location);
	}
}
