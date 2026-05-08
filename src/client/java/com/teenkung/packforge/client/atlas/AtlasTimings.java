package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class AtlasTimings {
	private static final ConcurrentHashMap<Identifier, Timing> TIMINGS = new ConcurrentHashMap<>();

	public static long start() {
		return FeatureFlags.atlasPhaseTimingsEnabled() ? System.nanoTime() : 0L;
	}

	public static void recordSource(Identifier atlas, long startNs) {
		record(atlas, startNs, Phase.SOURCE);
	}

	public static void recordDecode(Identifier atlas, long startNs) {
		record(atlas, startNs, Phase.DECODE);
	}

	public static void recordStitch(Identifier atlas, long startNs) {
		record(atlas, startNs, Phase.STITCH);
	}

	public static void recordMip(Identifier atlas, long startNs) {
		record(atlas, startNs, Phase.MIP);
	}

	public static void recordUpload(Identifier atlas, long startNs) {
		record(atlas, startNs, Phase.UPLOAD);
	}

	public static void logAtlas(Identifier atlas) {
		if (!FeatureFlags.atlasPhaseTimingsEnabled()) {
			return;
		}
		Timing timing = TIMINGS.get(atlas);
		if (timing == null) {
			return;
		}
		PackForge.LOGGER.info("PackForge atlas timings {}: source={}ms decode={}ms stitch={}ms mip={}ms upload={}ms",
			atlas, ms(timing.source.sum()), ms(timing.decode.sum()), ms(timing.stitch.sum()), ms(timing.mip.sum()), ms(timing.upload.sum()));
		writeCsv(atlas, timing);
	}

	public static void resetForReload() {
		TIMINGS.clear();
	}

	private static void record(Identifier atlas, long startNs, Phase phase) {
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

	private static void writeCsv(Identifier atlas, Timing timing) {
		try {
			Path csv = Path.of("logs", "packforge-atlas-timings.csv");
			Files.createDirectories(csv.getParent());
			boolean exists = Files.exists(csv);
			try (var w = Files.newBufferedWriter(csv, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				if (!exists) {
					w.write("timestamp,atlas,source_ms,decode_ms,stitch_ms,mip_ms,upload_ms\n");
				}
				w.write(System.currentTimeMillis() + "," + csv(atlas.toString()) + "," + ms(timing.source.sum()) + "," +
					ms(timing.decode.sum()) + "," + ms(timing.stitch.sum()) + "," + ms(timing.mip.sum()) + "," +
					ms(timing.upload.sum()) + "\n");
			}
		} catch (IOException e) {
			PackForge.LOGGER.warn("Failed to write atlas timings CSV", e);
		}
	}

	private static String csv(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	private enum Phase {
		SOURCE,
		DECODE,
		STITCH,
		MIP,
		UPLOAD
	}

	private static final class Timing {
		final LongAdder source = new LongAdder();
		final LongAdder decode = new LongAdder();
		final LongAdder stitch = new LongAdder();
		final LongAdder mip = new LongAdder();
		final LongAdder upload = new LongAdder();
	}

	private AtlasTimings() {}
}
