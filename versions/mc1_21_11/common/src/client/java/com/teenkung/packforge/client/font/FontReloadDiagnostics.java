package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.diagnostics.AsyncDiagnosticCsv;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Reload diagnostics for the 1.21.11 coordinated font preparation path. */
public final class FontReloadDiagnostics {
	private static final ThreadLocal<ApplyStats> APPLY_STATS = new ThreadLocal<>();

	public static Snapshot snapshot(Map<Identifier, List<GlyphProvider.Conditional>> fontSets,
		long selectionNs, int memoHits, int memoMisses, int uniqueStacks) {
		return snapshot(fontSets, selectionNs, memoHits, memoMisses, uniqueStacks, enabled());
	}

	public static Snapshot snapshot(Map<Identifier, List<GlyphProvider.Conditional>> fontSets,
		long selectionNs, int memoHits, int memoMisses, int uniqueStacks, boolean enabled) {
		if (!enabled) return Snapshot.EMPTY;
		int providerCount = fontSets.values().stream().mapToInt(List::size).sum();
		return new Snapshot(fontSets.size(), providerCount, selectionNs, memoHits, memoMisses, uniqueStacks);
	}

	public static void startApply() {
		if (enabled()) APPLY_STATS.set(new ApplyStats(System.nanoTime()));
	}

	public static void recordFontSetCreate(long elapsedNs, boolean optimized) {
		ApplyStats stats = APPLY_STATS.get();
		if (stats == null) return;
		stats.fontSetCreateNs += elapsedNs;
		stats.fontSets++;
		if (optimized) stats.optimizedFontSets++;
	}

	public static void finishApply(Object preparation, FontPreparationBundle bundle) {
		ApplyStats stats = APPLY_STATS.get();
		APPLY_STATS.remove();
		if (!enabled() || stats == null) return;
		long totalNs = System.nanoTime() - stats.startNs;
		Snapshot snapshot = bundle == null ? Snapshot.EMPTY : bundle.diagnostics();
		PackForge.LOGGER.info(
			"PackForge font reload: apply={}ms fontSetCreate={}ms fontSets={} optimized={} fonts={} providers={} selectionPrepare={}ms memoHits={} memoMisses={} uniqueStacks={}",
			ms(totalNs), ms(stats.fontSetCreateNs), stats.fontSets, stats.optimizedFontSets,
			snapshot.fonts(), snapshot.providers(), ms(snapshot.selectionPrepareNs()), snapshot.memoHits(),
			snapshot.memoMisses(), snapshot.uniqueStacks()
		);
		String row = System.currentTimeMillis() + "," + ms(totalNs) + "," + ms(stats.fontSetCreateNs) + ","
			+ stats.fontSets + "," + stats.optimizedFontSets + "," + snapshot.fonts() + "," + snapshot.providers() + ","
			+ ms(snapshot.selectionPrepareNs()) + "," + snapshot.memoHits() + "," + snapshot.memoMisses() + ","
			+ snapshot.uniqueStacks();
		AsyncDiagnosticCsv.append(
			Path.of("logs", "packforge-font-timings.csv"),
			"timestamp,apply_ms,font_set_create_ms,font_sets,optimized_font_sets,fonts,providers,selection_prepare_ms,memo_hits,memo_misses,unique_stacks",
			List.of(row)
		);
	}

	private static long ms(long ns) { return ns / 1_000_000L; }

	private static boolean enabled() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null
			? FeatureFlags.fontReloadDiagnosticsEnabled()
			: context.features().fontReloadDiagnosticsEnabled();
	}

	private static final class ApplyStats {
		private final long startNs;
		private long fontSetCreateNs;
		private int fontSets;
		private int optimizedFontSets;

		private ApplyStats(long startNs) { this.startNs = startNs; }
	}

	public record Snapshot(int fonts, int providers, long selectionPrepareNs,
		int memoHits, int memoMisses, int uniqueStacks) {
		static final Snapshot EMPTY = new Snapshot(0, 0, 0L, 0, 0, 0);
	}

	private FontReloadDiagnostics() {}
}
