package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.diagnostics.AsyncDiagnosticCsv;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FontReloadDiagnostics {
	private static final ThreadLocal<ApplyStats> APPLY_STATS = new ThreadLocal<>();

	public static void startApply(Map<ResourceLocation, List<GlyphProvider>> providers) {
		if (!FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return;
		}
		int providerCount = providers.values().stream().mapToInt(List::size).sum();
		APPLY_STATS.set(new ApplyStats(System.nanoTime(), providers.size(), providerCount));
	}

	public static void recordFontSet(long elapsedNs) {
		ApplyStats stats = APPLY_STATS.get();
		if (stats != null) {
			stats.fontSetNs += elapsedNs;
			stats.fontSets++;
		}
	}

	public static void finishApply() {
		ApplyStats stats = APPLY_STATS.get();
		APPLY_STATS.remove();
		if (!FeatureFlags.fontReloadDiagnosticsEnabled() || stats == null) {
			return;
		}
		long applyNs = System.nanoTime() - stats.startNs;
		PackForge.LOGGER.info(
			"PackForge font reload: apply={}ms fontSetCreate={}ms fontSets={} fonts={} providers={}",
			ms(applyNs),
			ms(stats.fontSetNs),
			stats.fontSets,
			stats.fonts,
			stats.providers
		);
		String row = System.currentTimeMillis() + "," + ms(applyNs) + "," + ms(stats.fontSetNs) + ","
			+ stats.fontSets + "," + stats.fonts + "," + stats.providers;
		AsyncDiagnosticCsv.append(
			Path.of("logs", "packforge-font-timings.csv"),
			"timestamp,apply_ms,font_set_create_ms,font_sets,fonts,providers",
			List.of(row)
		);
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	private static final class ApplyStats {
		private final long startNs;
		private final int fonts;
		private final int providers;
		private long fontSetNs;
		private int fontSets;

		private ApplyStats(long startNs, int fonts, int providers) {
			this.startNs = startNs;
			this.fonts = fonts;
			this.providers = providers;
		}
	}

	private FontReloadDiagnostics() {}
}
