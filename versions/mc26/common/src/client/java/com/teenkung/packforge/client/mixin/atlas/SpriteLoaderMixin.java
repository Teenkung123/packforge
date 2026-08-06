package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.client.atlas.AtlasReport;
import com.teenkung.packforge.client.atlas.AtlasRetry;
import com.teenkung.packforge.client.atlas.AtlasTimings;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Narrow mc26 sprite hooks; vanilla load/stitch control flow remains authoritative. */
@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@Shadow @Final private Identifier location;
	@Unique private static final ThreadLocal<Deque<PackforgeAtlasLoad>> PACKFORGE_ATLAS_LOADS = ThreadLocal.withInitial(ArrayDeque::new);

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
		PackforgeAtlasLoad invocation = PACKFORGE_ATLAS_LOADS.get().peek();
		if (invocation == null || invocation.state != null) {
			AtlasRetry.logRetryUnavailable(this.location);
			return vanilla;
		}
		SpriteMetadataCache.AtlasState state = SpriteMetadataCache.bind(this.location, plan);
		invocation.state = state;
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
		PackforgeAtlasLoad invocation = new PackforgeAtlasLoad(
			atlas,
			ResourcePackUnboundedBridge.configuredOwner(atlas)
		);
		Deque<PackforgeAtlasLoad> invocations = PACKFORGE_ATLAS_LOADS.get();
		invocations.push(invocation);
		try {
			CompletableFuture<SpriteLoader.Preparations> future = original.call(resourceManager, atlasId, mipLevel, executor, additional);
			SpriteMetadataCache.AtlasState state = invocation.state;
			if (state == null) {
				return future;
			}
			if (future == null) {
				SpriteMetadataCache.fail(state, null);
				return null;
			}
			future.whenComplete((ignored, error) -> {
				if (error != null) {
					SpriteMetadataCache.fail(state, null);
				} else {
					AtlasReport.logAtlas(atlas, state);
					SpriteMetadataCache.finish(state);
				}
				AtlasTimings.logAtlas(atlas);
			});
			return future;
		} catch (RuntimeException | Error failure) {
			SpriteMetadataCache.fail(invocation.state, null);
			throw failure;
		} finally {
			invocations.removeFirstOccurrence(invocation);
			if (invocations.isEmpty()) {
				PACKFORGE_ATLAS_LOADS.remove();
			}
		}
	}

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations packforge$retryOriginalStitch(
		List<SpriteContents> sprites,
		int mipLevel,
		Executor executor,
		Operation<SpriteLoader.Preparations> original
	) {
		SpriteMetadataCache.AtlasState state = SpriteMetadataCache.findState(this.location, sprites);
		if (state == null) {
			return original.call(sprites, mipLevel, executor);
		}
		return AtlasRetry.stitch(
			this.location,
			sprites,
			mipLevel,
			executor,
			(originalSprites, originalMipLevel, originalExecutor) ->
				original.call(originalSprites, originalMipLevel, originalExecutor),
			state
		);
	}

	@WrapMethod(method = "runSpriteSuppliers")
	private static CompletableFuture<List<SpriteContents>> packforge$decodeBounded(
		SpriteResourceLoader resourceLoader,
		List<SpriteSource.Loader> loaders,
		Executor executor,
		Operation<CompletableFuture<List<SpriteContents>>> original
	) {
		PackforgeAtlasLoad invocation = PACKFORGE_ATLAS_LOADS.get().peek();
		if (invocation != null && invocation.resourcePackUnboundedOwner) {
			return original.call(resourceLoader, loaders, executor);
		}

		BoundedSpriteDecode.Plan plan = BoundedSpriteDecode.capturePlan();
		if (!plan.decodeEnabled() && !plan.phaseTimingsEnabled()) {
			return original.call(resourceLoader, loaders, executor);
		}

		long startNs = AtlasTimings.start();
		String atlas = invocation == null ? "unknown" : invocation.atlas.toString();
		CompletableFuture<List<SpriteContents>> future = plan.decodeEnabled()
			? BoundedSpriteDecode.decode(loaders, executor, plan, loader -> loader.get(resourceLoader))
			: original.call(resourceLoader, loaders, executor);
		return plan.phaseTimingsEnabled()
			? future.whenComplete((ignored, error) -> AtlasTimings.recordDecode(atlas, startNs))
			: future;
	}

	private boolean packforge$resourcePackUnboundedOwnsAtlas() {
		return ResourcePackUnboundedBridge.configuredOwner(this.location);
	}

	@Unique
	private static final class PackforgeAtlasLoad {
		private final Identifier atlas;
		private final boolean resourcePackUnboundedOwner;
		private SpriteMetadataCache.AtlasState state;

		private PackforgeAtlasLoad(Identifier atlas, boolean resourcePackUnboundedOwner) {
			this.atlas = atlas;
			this.resourcePackUnboundedOwner = resourcePackUnboundedOwner;
		}
	}
}
