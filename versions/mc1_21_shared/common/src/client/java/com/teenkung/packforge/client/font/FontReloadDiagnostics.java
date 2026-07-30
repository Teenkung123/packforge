package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.diagnostics.AsyncDiagnosticCsv;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FontReloadDiagnostics {
	private static final ThreadLocal<ApplyStats> APPLY_STATS = new ThreadLocal<>();

	public static Snapshot snapshot(
		Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets,
		long selectionNs,
		int memoHits,
		int memoMisses,
		int uniqueStacks
	) {
		if (!FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return Snapshot.EMPTY;
		}
		int providerCount = 0;
		Map<String, Integer> providerTypes = new HashMap<>();
		for (List<GlyphProvider.Conditional> providers : fontSets.values()) {
			providerCount += providers.size();
			for (GlyphProvider.Conditional provider : providers) {
				providerTypes.merge(provider.provider().getClass().getSimpleName(), 1, Integer::sum);
			}
		}
		return new Snapshot(fontSets.size(), providerCount, Map.copyOf(providerTypes), selectionNs, memoHits, memoMisses, uniqueStacks);
	}

	public static void startApply() {
		if (FeatureFlags.fontReloadDiagnosticsEnabled()) {
			APPLY_STATS.set(new ApplyStats(System.nanoTime()));
		}
	}

	public static void recordFontSetCreate(long elapsedNs, boolean optimized) {
		ApplyStats stats = APPLY_STATS.get();
		if (stats != null) {
			stats.fontSetCreateNs += elapsedNs;
			stats.fontSets++;
			if (optimized) stats.optimizedFontSets++;
		}
	}

	public static void finishApply(FontPreparationBundle bundle) {
		ApplyStats stats = APPLY_STATS.get();
		APPLY_STATS.remove();
		if (!FeatureFlags.fontReloadDiagnosticsEnabled() || stats == null) {
			return;
		}
		long totalNs = System.nanoTime() - stats.startNs;
		Snapshot snapshot = bundle == null ? Snapshot.EMPTY : bundle.diagnostics();
		PackForge.LOGGER.info(
			"PackForge font reload: apply={}ms fontSetCreate={}ms fontSets={} optimized={} fonts={} providers={} selectionPrepare={}ms",
			ms(totalNs), ms(stats.fontSetCreateNs), stats.fontSets, stats.optimizedFontSets,
			snapshot.fonts(), snapshot.providers(), ms(snapshot.selectionPrepareNs())
		);
		String row = System.currentTimeMillis() + "," + ms(totalNs) + "," + ms(stats.fontSetCreateNs) + ","
			+ stats.fontSets + "," + stats.optimizedFontSets + "," + snapshot.fonts() + "," + snapshot.providers() + ","
			+ ms(snapshot.selectionPrepareNs()) + "," + snapshot.memoHits() + "," + snapshot.memoMisses() + ","
			+ snapshot.uniqueStacks() + "," + csv(snapshot.providerTypes().toString());
		AsyncDiagnosticCsv.append(
			Path.of("logs", "packforge-font-timings.csv"),
			"timestamp,apply_ms,font_set_create_ms,font_sets,optimized_font_sets,fonts,providers,selection_prepare_ms,memo_hits,memo_misses,unique_stacks,provider_types",
			List.of(row)
		);
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	private static String csv(String value) {
		return value.indexOf(',') < 0 && value.indexOf('"') < 0
			? value
			: "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static final class ApplyStats {
		private final long startNs;
		private long fontSetCreateNs;
		private int fontSets;
		private int optimizedFontSets;

		private ApplyStats(long startNs) {
			this.startNs = startNs;
		}
	}

	public record Snapshot(
		int fonts,
		int providers,
		Map<String, Integer> providerTypes,
		long selectionPrepareNs,
		int memoHits,
		int memoMisses,
		int uniqueStacks
	) {
		private static final Snapshot EMPTY = new Snapshot(0, 0, Map.of(), 0L, 0, 0, 0);
	}

	private FontReloadDiagnostics() {}
}
