package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FontReloadDiagnostics {
	private static final ThreadLocal<ApplyStats> APPLY_STATS = new ThreadLocal<>();

	public static Snapshot snapshot(Map<Identifier, List<GlyphProvider.Conditional>> fontSets, long selectionNs) {
		if (!FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return Snapshot.EMPTY;
		}
		int providerCount = 0;
		HashMap<String, Integer> providerTypes = new HashMap<>();
		for (List<GlyphProvider.Conditional> providers : fontSets.values()) {
			providerCount += providers.size();
			for (GlyphProvider.Conditional provider : providers) {
				String type = provider.provider().getClass().getSimpleName();
				providerTypes.merge(type, 1, Integer::sum);
			}
		}
		return new Snapshot(fontSets.size(), providerCount, providerTypes, selectionNs);
	}

	public static void startApply() {
		if (FeatureFlags.fontReloadDiagnosticsEnabled()) {
			APPLY_STATS.set(new ApplyStats(System.nanoTime()));
		}
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
		if (!FeatureFlags.fontReloadDiagnosticsEnabled() || stats == null) {
			return;
		}
		long totalNs = System.nanoTime() - stats.startNs;
		Snapshot snapshot = bundle != null ? bundle.diagnostics() : Snapshot.EMPTY;
		PackForge.LOGGER.info("PackForge font reload: apply={}ms fontSetCreate={}ms fontSets={} optimized={} fonts={} providers={} selectionPrepare={}ms providerTypes={}",
			ms(totalNs), ms(stats.fontSetCreateNs), stats.fontSets, stats.optimizedFontSets,
			snapshot.fonts, snapshot.providers, ms(snapshot.selectionPrepareNs), snapshot.providerTypes);
		writeCsv(totalNs, stats, snapshot);
	}

	private static void writeCsv(long totalNs, ApplyStats stats, Snapshot snapshot) {
		try {
			Path csv = Path.of("logs", "packforge-font-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) {
					w.write("timestamp,apply_ms,font_set_create_ms,font_sets,optimized_font_sets,fonts,providers,selection_prepare_ms,provider_types\n");
				}
				w.write(System.currentTimeMillis() + "," + ms(totalNs) + "," + ms(stats.fontSetCreateNs) + "," +
					stats.fontSets + "," + stats.optimizedFontSets + "," + snapshot.fonts + "," + snapshot.providers + "," +
					ms(snapshot.selectionPrepareNs) + "," + csv(snapshot.providerTypes.toString()) + "\n");
			}
		} catch (IOException e) {
			PackForge.LOGGER.warn("Failed to write font timings CSV", e);
		}
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	private static String csv(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private FontReloadDiagnostics() {}

	private static final class ApplyStats {
		final long startNs;
		long fontSetCreateNs;
		int fontSets;
		int optimizedFontSets;

		ApplyStats(long startNs) {
			this.startNs = startNs;
		}
	}

	public record Snapshot(int fonts, int providers, Map<String, Integer> providerTypes, long selectionPrepareNs) {
		static final Snapshot EMPTY = new Snapshot(0, 0, Map.of(), 0L);
	}
}
