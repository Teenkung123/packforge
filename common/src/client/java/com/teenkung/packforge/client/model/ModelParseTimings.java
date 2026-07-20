package com.teenkung.packforge.client.model;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;

import java.util.concurrent.atomic.LongAdder;

public final class ModelParseTimings {
	private static final LongAdder listNs = new LongAdder();
	private static final LongAdder readNs = new LongAdder();
	private static final LongAdder parseNs = new LongAdder();
	private static final LongAdder collectNs = new LongAdder();
	private static final LongAdder models = new LongAdder();
	private static final LongAdder batches = new LongAdder();

	public static long start() {
		return FeatureFlags.modelParseTimingEnabled() ? System.nanoTime() : 0L;
	}

	public static void recordList(long startNs) {
		record(listNs, startNs);
	}

	public static void recordRead(long startNs) {
		record(readNs, startNs);
	}

	public static void recordParse(long startNs) {
		record(parseNs, startNs);
		models.increment();
	}

	public static void recordCollect(long startNs) {
		record(collectNs, startNs);
	}

	public static void recordBatch() {
		if (FeatureFlags.modelParseTimingEnabled()) {
			batches.increment();
		}
	}

	public static void log() {
		if (!FeatureFlags.modelParseTimingEnabled()) {
			return;
		}
		PackForge.LOGGER.info("PackForge model parse timings: list={}ms read={}ms parse={}ms collect={}ms models={} batches={}",
			ms(listNs.sum()), ms(readNs.sum()), ms(parseNs.sum()), ms(collectNs.sum()), models.sum(), batches.sum());
	}

	public static void reset() {
		listNs.reset();
		readNs.reset();
		parseNs.reset();
		collectNs.reset();
		models.reset();
		batches.reset();
	}

	private static void record(LongAdder adder, long startNs) {
		if (startNs != 0L && FeatureFlags.modelParseTimingEnabled()) {
			adder.add(System.nanoTime() - startNs);
		}
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	private ModelParseTimings() {}
}
