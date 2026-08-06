package com.teenkung.packforge.concurrent;

import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;

import java.util.Objects;

/**
 * Reload-scoped model scheduling decision shared by every Minecraft adapter.
 *
 * <p>The direct parser is deliberately not a selectable result. Any model
 * feature request uses the original vanilla loader behind a bounded drainer;
 * with no request, the original executor is passed through unchanged.</p>
 */
public record ModelSchedulingPlan(Strategy strategy, int workerBudget) {
	public ModelSchedulingPlan {
		Objects.requireNonNull(strategy, "strategy");
		workerBudget = Math.max(1, workerBudget);
	}

	/**
	 * Reads the immutable reload snapshot when a reload is active. Outside a
	 * reload, capture is the only place that consults the mutable feature flags.
	 */
	public static ModelSchedulingPlan current() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return from(context == null ? ReloadFeatureSnapshot.capture() : context.features());
	}

	public static ModelSchedulingPlan from(ReloadFeatureSnapshot features) {
		Objects.requireNonNull(features, "features");
		return fromFlags(
			features.modelParseBatchingEnabled(),
			features.modelParseTimingEnabled(),
			features.modelAdaptiveBatchingEnabled(),
			features.modelDuplicateParseCacheEnabled(),
			features.workerBudget()
		);
	}

	static ModelSchedulingPlan fromFlags(
		boolean batching,
		boolean timing,
		boolean adaptive,
		boolean duplicateCache,
		int workerBudget
	) {
		boolean requested = batching || timing || adaptive || duplicateCache;
		return new ModelSchedulingPlan(
			requested ? Strategy.COALESCED_ORIGINAL : Strategy.ORIGINAL,
			workerBudget
		);
	}

	public enum Strategy {
		/** Reserved, unreachable until direct hook safety is proven. */
		DIRECT_BATCHED,
		COALESCED_ORIGINAL,
		ORIGINAL
	}
}
