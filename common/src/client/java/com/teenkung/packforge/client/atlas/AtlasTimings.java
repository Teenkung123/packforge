package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.diagnostics.AsyncDiagnosticCsv;
import com.teenkung.packforge.config.FeatureFlags;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Loader-neutral atlas phase timing used by all Minecraft API adapters. */
public final class AtlasTimings {
	private static final ConcurrentHashMap<String, Timing> TIMINGS = new ConcurrentHashMap<>();

	public static long start() {
		return FeatureFlags.atlasPhaseTimingsEnabled() ? System.nanoTime() : 0L;
	}

	public static void recordSource(String atlas, long startNs) { record(atlas, startNs, Phase.SOURCE); }
	public static void recordDecode(String atlas, long startNs) { record(atlas, startNs, Phase.DECODE); }
	public static void recordStitch(String atlas, long startNs) { record(atlas, startNs, Phase.STITCH); }
	public static void recordMip(String atlas, long startNs) { record(atlas, startNs, Phase.MIP); }
	public static void recordUpload(String atlas, long startNs) { record(atlas, startNs, Phase.UPLOAD); }
	public static void recordSource(Object atlas, long startNs) { recordSource(atlas.toString(), startNs); }
	public static void recordDecode(Object atlas, long startNs) { recordDecode(atlas.toString(), startNs); }
	public static void recordStitch(Object atlas, long startNs) { recordStitch(atlas.toString(), startNs); }
	public static void recordMip(Object atlas, long startNs) { recordMip(atlas.toString(), startNs); }
	public static void recordUpload(Object atlas, long startNs) { recordUpload(atlas.toString(), startNs); }

	public static void logAtlas(String atlas) {
		if (!FeatureFlags.atlasPhaseTimingsEnabled()) {
			return;
		}
		Timing timing = TIMINGS.get(atlas);
		if (timing == null) {
			return;
		}
		PackForge.LOGGER.info(
			"PackForge atlas timings {}: source={}ms decode={}ms stitch={}ms mip={}ms upload={}ms",
			atlas,
			ms(timing.source.sum()),
			ms(timing.decode.sum()),
			ms(timing.stitch.sum()),
			ms(timing.mip.sum()),
			ms(timing.upload.sum())
		);
		String row = System.currentTimeMillis() + "," + csv(atlas) + "," + ms(timing.source.sum()) + ","
			+ ms(timing.decode.sum()) + "," + ms(timing.stitch.sum()) + "," + ms(timing.mip.sum()) + ","
			+ ms(timing.upload.sum());
		AsyncDiagnosticCsv.append(
			Path.of("logs", "packforge-atlas-timings.csv"),
			"timestamp,atlas,source_ms,decode_ms,stitch_ms,mip_ms,upload_ms",
			List.of(row)
		);
	}

	public static void logAtlas(Object atlas) {
		logAtlas(atlas.toString());
	}

	public static void resetForReload() {
		TIMINGS.clear();
	}

	private static void record(String atlas, long startNs, Phase phase) {
		if (startNs == 0L || !FeatureFlags.atlasPhaseTimingsEnabled()) {
			return;
		}
		long elapsed = System.nanoTime() - startNs;
		Timing timing = TIMINGS.computeIfAbsent(atlas, ignored -> new Timing());
		switch (phase) {
			case SOURCE -> timing.source.add(elapsed);
			case DECODE -> timing.decode.add(elapsed);
			case STITCH -> timing.stitch.add(elapsed);
			case MIP -> timing.mip.add(elapsed);
			case UPLOAD -> timing.upload.add(elapsed);
		}
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	private static String csv(String value) {
		return value.indexOf(',') < 0 && value.indexOf('"') < 0
			? value
			: "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private enum Phase { SOURCE, DECODE, STITCH, MIP, UPLOAD }

	private static final class Timing {
		private final LongAdder source = new LongAdder();
		private final LongAdder decode = new LongAdder();
		private final LongAdder stitch = new LongAdder();
		private final LongAdder mip = new LongAdder();
		private final LongAdder upload = new LongAdder();
	}

	private AtlasTimings() {}
}
