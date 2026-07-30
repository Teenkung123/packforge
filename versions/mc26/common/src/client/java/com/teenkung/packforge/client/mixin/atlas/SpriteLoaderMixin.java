package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.client.atlas.AtlasTimings;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.atlas.AtlasReport;
import com.teenkung.packforge.client.atlas.AtlasRetry;
import com.teenkung.packforge.client.atlas.CappedSpriteResourceLoader;
import com.teenkung.packforge.client.compat.ResourcePackUnboundedBridge;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.StitcherException;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
	@Shadow @Final private Identifier location;
	@Shadow @Final private int maxSupportedTextureSize;

	@Invoker("getStitchedSprites")
	protected abstract Map<Identifier, TextureAtlasSprite> packforge$getStitchedSprites(Stitcher<SpriteContents> stitcher, int atlasWidth, int atlasHeight);

	@Redirect(
		method = "loadAndStitch",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/texture/atlas/SpriteResourceLoader;create(Ljava/util/Set;)Lnet/minecraft/client/renderer/texture/atlas/SpriteResourceLoader;"
		)
	)
	private SpriteResourceLoader packforge$wrapLoader(Set<MetadataSectionType<?>> additional) {
		if (packforge$resourcePackUnboundedOwnsAtlas()
			|| !FeatureFlags.atlasCapEnabled()
			|| FeatureFlags.atlasExcludes(this.location.toString())) {
			return SpriteResourceLoader.create(additional);
		}
		return new CappedSpriteResourceLoader(this.location, additional);
	}

	@Inject(method = "loadAndStitch", at = @At("RETURN"))
	private void packforge$reportAtlas(CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> cir) {
		cir.getReturnValue().thenRun(() -> {
			if (!packforge$resourcePackUnboundedOwnsAtlas()) {
				AtlasReport.logAtlas(this.location);
			}
			AtlasTimings.logAtlas(this.location);
		});
	}

	@WrapMethod(method = "loadAndStitch")
	private CompletableFuture<SpriteLoader.Preparations> packforge$timeLoadAndStitch(
		ResourceManager manager,
		Identifier atlasInfoLocation,
		int maxMipmapLevels,
		Executor taskExecutor,
		Set<MetadataSectionType<?>> additionalMetadata,
		Operation<CompletableFuture<SpriteLoader.Preparations>> original
	) {
		if (packforge$resourcePackUnboundedOwnsAtlas()) {
			return original.call(manager, atlasInfoLocation, maxMipmapLevels, taskExecutor, additionalMetadata);
		}
		if (!FeatureFlags.atlasPhaseTimingsEnabled() && !FeatureFlags.atlasDecodeBatchingEnabled()) {
			return original.call(manager, atlasInfoLocation, maxMipmapLevels, taskExecutor, additionalMetadata);
		}
		SpriteResourceLoader spriteResourceLoader = packforge$createResourceLoader(additionalMetadata);
		return CompletableFuture.supplyAsync(() -> {
			long startNs = AtlasTimings.start();
			List<SpriteSource.Loader> loaders = SpriteSourceList.load(manager, atlasInfoLocation).list(manager);
			AtlasTimings.recordSource(this.location, startNs);
			return loaders;
		}, taskExecutor).thenCompose(loaders -> {
			long startNs = AtlasTimings.start();
			List<CompletableFuture<List<SpriteContents>>> spriteFutures = packforge$decodeSpriteBatches(loaders, spriteResourceLoader, taskExecutor);
			return Util.sequence(spriteFutures).thenApply(batches -> {
				AtlasTimings.recordDecode(this.location, startNs);
				return batches.stream().flatMap(List::stream).filter(Objects::nonNull).toList();
			});
		}).thenApply(sprites -> this.packforge$stitchWithFeatures(sprites, maxMipmapLevels, taskExecutor));
	}

	@WrapMethod(method = "stitch")
	private SpriteLoader.Preparations packforge$retryStitch(List<SpriteContents> sprites, int maxMipmapLevels, Executor executor, Operation<SpriteLoader.Preparations> original) {
		if (packforge$resourcePackUnboundedOwnsAtlas()) {
			return original.call(sprites, maxMipmapLevels, executor);
		}
		if (!FeatureFlags.atlasRetryEnabled() && !FeatureFlags.atlasMipParallelEnabled() && !FeatureFlags.atlasPhaseTimingsEnabled()) {
			return original.call(sprites, maxMipmapLevels, executor);
		}
		return packforge$stitchWithFeatures(sprites, maxMipmapLevels, executor);
	}

	private SpriteLoader.Preparations packforge$stitchWithFeatures(List<SpriteContents> sprites, int maxMipmapLevels, Executor executor) {
		if (!FeatureFlags.atlasRetryEnabled() || FeatureFlags.atlasExcludes(this.location.toString())) {
			return packforge$stitchOnce(sprites, maxMipmapLevels, executor);
		}
		int maxAttempts = FeatureFlags.atlasRetryMaxAttempts();
		List<SpriteContents> current = sprites;
		ReportedException last = null;
		for (int attempt = 0; attempt <= maxAttempts; attempt++) {
			try {
				return packforge$stitchOnce(current, maxMipmapLevels, executor);
			} catch (ReportedException e) {
				if (!(e.getCause() instanceof StitcherException)) throw e;
				last = e;
				if (attempt == maxAttempts) break;
				PackForge.LOGGER.warn("PackForge atlas {} stitch failed (attempt {}); retrying with halved sprites", this.location, attempt + 1);
				current = AtlasRetry.halveAll(current, this.location);
			}
		}
		PackForge.LOGGER.error("PackForge atlas {} retry exhausted after {} attempts", this.location, maxAttempts);
		throw last;
	}

	private SpriteLoader.Preparations packforge$stitchOnce(List<SpriteContents> sprites, int maxMipmapLevels, Executor executor) {
		long stitchStartNs = AtlasTimings.start();
		try (Zone ignored = Profiler.get().zone(() -> "stitch " + this.location)) {
			int mipLevel;
			int maxTextureSize = this.maxSupportedTextureSize;
			int minTexelSize = Integer.MAX_VALUE;
			int lowestOneBit = 1 << maxMipmapLevels;
			for (SpriteContents spriteInfo : sprites) {
				minTexelSize = Math.min(minTexelSize, Math.min(spriteInfo.width(), spriteInfo.height()));
				int lowestTextureBit = Math.min(Integer.lowestOneBit(spriteInfo.width()), Integer.lowestOneBit(spriteInfo.height()));
				if (lowestTextureBit >= lowestOneBit) continue;
				PackForge.LOGGER.warn("Texture {} with size {}x{} limits mip level from {} to {}", spriteInfo.name(), spriteInfo.width(), spriteInfo.height(), Mth.log2(lowestOneBit), Mth.log2(lowestTextureBit));
				lowestOneBit = lowestTextureBit;
			}
			int minSize = Math.min(minTexelSize, lowestOneBit);
			int minPowerOfTwo = Mth.log2(minSize);
			if (minPowerOfTwo < maxMipmapLevels) {
				PackForge.LOGGER.warn("{}: dropping miplevel from {} to {}, because of minimum power of two: {}", this.location, maxMipmapLevels, minPowerOfTwo, minSize);
				mipLevel = minPowerOfTwo;
			} else {
				mipLevel = maxMipmapLevels;
			}
			Options options = Minecraft.getInstance().options;
			int anisotropyBit = options.textureFiltering().get() != TextureFilteringMethod.ANISOTROPIC ? 0 : options.maxAnisotropyBit().get();
			Stitcher<SpriteContents> stitcher = new Stitcher<>(maxTextureSize, maxTextureSize, mipLevel, anisotropyBit);
			for (SpriteContents spriteInfo : sprites) {
				stitcher.registerSprite(spriteInfo);
			}
			try {
				stitcher.stitch();
			} catch (StitcherException e) {
				CrashReport report = CrashReport.forThrowable(e, "Stitching");
				CrashReportCategory category = report.addCategory("Stitcher");
				category.setDetail("Sprites", e.getAllSprites().stream().map(s -> String.format(Locale.ROOT, "%s[%dx%d]", s.name(), s.width(), s.height())).collect(Collectors.joining(",")));
				category.setDetail("Max Texture Size", maxTextureSize);
				throw new ReportedException(report);
			}
			int width = stitcher.getWidth();
			int height = stitcher.getHeight();
			Map<Identifier, TextureAtlasSprite> result = new HashMap<>(this.packforge$getStitchedSprites(stitcher, width, height));
			TextureAtlasSprite missingSprite = result.get(MissingTextureAtlasSprite.getLocation());
			AtlasTimings.recordStitch(this.location, stitchStartNs);
			int finalMipLevel = mipLevel;
			CompletableFuture<Void> readyForUpload = FeatureFlags.atlasMipParallelEnabled()
				? packforge$mipParallel(result, finalMipLevel, executor)
				: CompletableFuture.runAsync(() -> {
					long startNs = AtlasTimings.start();
					result.values().forEach(sprite -> sprite.contents().increaseMipLevel(finalMipLevel));
					AtlasTimings.recordMip(this.location, startNs);
				}, executor);
			return new SpriteLoader.Preparations(width, height, mipLevel, missingSprite, result, readyForUpload);
		}
	}

	private CompletableFuture<Void> packforge$mipParallel(Map<Identifier, TextureAtlasSprite> result, int mipLevel, Executor executor) {
		List<TextureAtlasSprite> sprites = List.copyOf(result.values());
		int batchSize = Math.max(16, FeatureFlags.atlasMipBatchSize());
		CompletableFuture<?>[] jobs = new CompletableFuture<?>[(sprites.size() + batchSize - 1) / batchSize];
		long startNs = AtlasTimings.start();
		for (int i = 0; i < jobs.length; i++) {
			int from = i * batchSize;
			int to = Math.min(sprites.size(), from + batchSize);
			jobs[i] = CompletableFuture.runAsync(() -> {
				for (int index = from; index < to; index++) {
					sprites.get(index).contents().increaseMipLevel(mipLevel);
				}
			}, executor);
		}
		return CompletableFuture.allOf(jobs).whenComplete((ignored, error) -> AtlasTimings.recordMip(this.location, startNs));
	}

	private SpriteResourceLoader packforge$createResourceLoader(Set<MetadataSectionType<?>> additional) {
		if (packforge$resourcePackUnboundedOwnsAtlas()
			|| !FeatureFlags.atlasCapEnabled()
			|| FeatureFlags.atlasExcludes(this.location.toString())) {
			return SpriteResourceLoader.create(additional);
		}
		return new CappedSpriteResourceLoader(this.location, additional);
	}

	private List<CompletableFuture<List<SpriteContents>>> packforge$decodeSpriteBatches(List<SpriteSource.Loader> loaders, SpriteResourceLoader spriteResourceLoader, Executor executor) {
		if (!FeatureFlags.atlasDecodeBatchingEnabled()) {
			return loaders.stream()
				.map(loader -> CompletableFuture.supplyAsync(() -> java.util.Collections.singletonList(loader.get(spriteResourceLoader)), executor))
				.toList();
		}
		int batchSize = Math.max(16, FeatureFlags.atlasDecodeBatchSize());
		return java.util.stream.IntStream.range(0, (loaders.size() + batchSize - 1) / batchSize)
			.mapToObj(batch -> {
				int from = batch * batchSize;
				int to = Math.min(loaders.size(), from + batchSize);
				return CompletableFuture.supplyAsync(() -> {
					java.util.ArrayList<SpriteContents> sprites = new java.util.ArrayList<>(to - from);
					for (int index = from; index < to; index++) {
						SpriteContents sprite = loaders.get(index).get(spriteResourceLoader);
						if (sprite != null) {
							sprites.add(sprite);
						}
					}
					return List.copyOf(sprites);
				}, executor);
			})
			.toList();
	}

	private boolean packforge$resourcePackUnboundedOwnsAtlas() {
		return ResourcePackUnboundedBridge.configuredOwner(this.location);
	}
}
