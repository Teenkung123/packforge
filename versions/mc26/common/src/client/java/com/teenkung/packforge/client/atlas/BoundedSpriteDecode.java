package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.concurrent.OrderedAsync;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.client.renderer.texture.SpriteContents;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bounded, ordered sprite decoding for the 26.x SpriteLoader adapter. */
public final class BoundedSpriteDecode {
	private BoundedSpriteDecode() {
	}

	public static Plan capturePlan() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context != null) {
			ReloadFeatureSnapshot features = context.features();
			return new Plan(
				features.atlasDecodeBatchingEnabled(),
				features.atlasPhaseTimingsEnabled(),
				features.atlasCapEnabled(),
				features.atlasCapPx(),
				features.atlasRetryEnabled(),
				features.atlasRetryMaxAttempts(),
				features.workerBudget(),
				features.atlasDecodeBatchSize(),
				features.atlasExclusionIds()
			);
		}

		return new Plan(
			FeatureFlags.atlasDecodeBatchingEnabled(),
			FeatureFlags.atlasPhaseTimingsEnabled(),
			FeatureFlags.atlasCapEnabled(),
			FeatureFlags.atlasCapPx(),
			FeatureFlags.atlasRetryEnabled(),
			FeatureFlags.atlasRetryMaxAttempts(),
			ReloadFeatureSnapshot.boundedWorkerBudget(0, Runtime.getRuntime().availableProcessors()),
			FeatureFlags.atlasDecodeBatchSize(),
			Set.copyOf(FeatureFlags.atlasExclusionIds())
		);
	}

	public static <I> CompletableFuture<List<SpriteContents>> decode(
		List<? extends I> inputs,
		Executor executor,
		Plan plan,
		Function<? super I, ? extends SpriteContents> decoder
	) {
		return decode(inputs, executor, plan, decoder, SpriteContents::close);
	}

	static <I, O> CompletableFuture<List<O>> decode(
		List<? extends I> inputs,
		Executor executor,
		Plan plan,
		Function<? super I, ? extends O> decoder,
		Consumer<? super O> disposer
	) {
		// Decode costs vary with image dimensions and compression. Claim one loader
		// at a time so a slow image cannot hold the rest of a contiguous batch.
		// OrderedAsync still submits only its bounded set of reusable workers.
		CompletableFuture<List<O>> mapped = OrderedAsync.map(
			inputs,
			executor,
			plan.workerBudget(),
			1,
			decoder,
			disposer
		);
		return filterAndPropagateCancellation(mapped, disposer);
	}

	private static <O> CompletableFuture<List<O>> filterAndPropagateCancellation(
		CompletableFuture<List<O>> mapped,
		Consumer<? super O> disposer
	) {
		CompletableFuture<List<O>> filtered = new CompletableFuture<>();
		mapped.whenComplete((decoded, error) -> {
			if (error != null) {
				filtered.completeExceptionally(error);
				return;
			}

			List<O> nonNull;
			try {
				nonNull = decoded.stream().filter(Objects::nonNull).toList();
			} catch (Throwable throwable) {
				closeAll(decoded, disposer);
				filtered.completeExceptionally(throwable);
				return;
			}

			if (!filtered.complete(nonNull)) {
				closeAll(decoded, disposer);
			}
		});
		filtered.whenComplete((ignored, error) -> {
			if (filtered.isCancelled()) {
				mapped.cancel(false);
			}
		});
		return filtered;
	}

	private static <O> void closeAll(List<O> sprites, Consumer<? super O> disposer) {
		for (O sprite : sprites) {
			if (sprite == null) {
				continue;
			}
			try {
				disposer.accept(sprite);
			} catch (Throwable throwable) {
				PackForge.LOGGER.error("Failed to close a decoded sprite after cancellation", throwable);
			}
		}
	}

	public record Plan(
		boolean decodeEnabled,
		boolean phaseTimingsEnabled,
		boolean atlasCapEnabled,
		int atlasCapPx,
		boolean atlasRetryEnabled,
		int atlasRetryMaxAttempts,
		int workerBudget,
		int chunkSize,
		Set<String> atlasExclusionIds
	) {
		public Plan {
			atlasCapPx = Math.max(1, atlasCapPx);
			atlasRetryMaxAttempts = Math.max(1, atlasRetryMaxAttempts);
			workerBudget = Math.max(1, workerBudget);
			chunkSize = Math.max(1, chunkSize);
			atlasExclusionIds = atlasExclusionIds == null ? Set.of() : Set.copyOf(atlasExclusionIds);
		}

		public boolean atlasCapApplies(String atlasId) {
			return atlasCapEnabled && !atlasExclusionIds.contains(atlasId);
		}

		public boolean atlasRetryApplies(String atlasId) {
			return atlasRetryEnabled && !atlasExclusionIds.contains(atlasId);
		}
	}
}
