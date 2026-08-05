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
import java.util.function.Function;

/** Bounded, ordered sprite decoding for the 1.20.1 SpriteLoader adapter. */
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
				features.workerBudget(),
				features.atlasDecodeBatchSize(),
				features.atlasExclusionIds()
			);
		}

		return new Plan(
			FeatureFlags.atlasDecodeBatchingEnabled(),
			FeatureFlags.atlasPhaseTimingsEnabled(),
			FeatureFlags.atlasCapEnabled(),
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
		CompletableFuture<List<SpriteContents>> mapped = OrderedAsync.map(
			inputs,
			executor,
			plan.workerBudget(),
			plan.chunkSize(),
			decoder,
			BoundedSpriteDecode::close
		);
		return filterAndPropagateCancellation(mapped);
	}

	private static CompletableFuture<List<SpriteContents>> filterAndPropagateCancellation(
		CompletableFuture<List<SpriteContents>> mapped
	) {
		CompletableFuture<List<SpriteContents>> filtered = new CompletableFuture<>();
		mapped.whenComplete((decoded, error) -> {
			if (error != null) {
				filtered.completeExceptionally(error);
				return;
			}

			List<SpriteContents> nonNull;
			try {
				nonNull = decoded.stream().filter(Objects::nonNull).toList();
			} catch (Throwable throwable) {
				closeAll(decoded);
				filtered.completeExceptionally(throwable);
				return;
			}

			if (!filtered.complete(nonNull)) {
				closeAll(decoded);
			}
		});
		filtered.whenComplete((ignored, error) -> {
			if (filtered.isCancelled()) {
				mapped.cancel(false);
			}
		});
		return filtered;
	}

	private static void close(SpriteContents sprite) {
		sprite.close();
	}

	private static void closeAll(List<SpriteContents> sprites) {
		for (SpriteContents sprite : sprites) {
			if (sprite == null) {
				continue;
			}
			try {
				sprite.close();
			} catch (Throwable throwable) {
				PackForge.LOGGER.error("Failed to close a decoded sprite after cancellation", throwable);
			}
		}
	}

	public record Plan(
		boolean decodeEnabled,
		boolean phaseTimingsEnabled,
		boolean atlasCapEnabled,
		int workerBudget,
		int chunkSize,
		Set<String> atlasExclusionIds
	) {
		public Plan {
			workerBudget = Math.max(1, workerBudget);
			chunkSize = Math.max(1, chunkSize);
			atlasExclusionIds = atlasExclusionIds == null ? Set.of() : Set.copyOf(atlasExclusionIds);
		}

		public boolean atlasCapApplies(String atlasId) {
			return atlasCapEnabled && !atlasExclusionIds.contains(atlasId);
		}
	}
}
