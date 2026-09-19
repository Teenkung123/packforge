package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Fixed-size, invocation-owned observations; no cross-reload atlas map or per-sprite log queue. */
public final class AtlasDiagnostics implements AutoCloseable {
	private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
	private static final ThreadLocal<ReadProbe> READING = new ThreadLocal<>();
	private static final int RESERVATION_BYTES = 4096;
	private final PreparationBudget.Reservation reservation;
	private final PreparationBudget.Scope budget;
	private PreparationBudget.Reservation loaderReservation;
	private boolean loadersObserved;
	private boolean closing;
	private int owners;
	private long ownerLimit = 16;
	private final long reloadId;
	private final String atlas;
	private long spriteCount;
	private long spriteWallNs;
	private long spriteCpuNs;
	private boolean spriteCpuAvailable = true;
	private long spriteReadNs;
	private long spriteReadBytes;
	private long slowestNs;
	private long slowestCpuNs;
	private long slowestReadNs;
	private String slowestSprite = "none";
	private long supplierCount;
	private long slowestSupplierNs;
	private long slowestSupplierCpuNs;
	private String slowestSupplier = "none";

	AtlasDiagnostics(PreparationBudget.Scope budget, PreparationBudget.Reservation reservation, long reloadId, String atlas) {
		this.budget = budget;
		this.reservation = reservation;
		this.reloadId = reloadId;
		this.atlas = bounded(atlas);
	}

	public static AtlasDiagnostics capture(String atlas) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context == null || !context.features().atlasPhaseTimingsEnabled()) return null;
		PreparationBudget.Reservation reservation = context.preparationBudget().tryReserve(RESERVATION_BYTES);
		if (reservation == null) {
			PackForge.LOGGER.info("PackForge atlas phase: reload={} atlas={} stage=diagnostics_skipped reason=memory_budget", context.reloadId(), atlas);
			return null;
		}
		return new AtlasDiagnostics(context.preparationBudget(), reservation, context.reloadId(), atlas);
	}

	public synchronized List<SpriteSource.Loader> observeLoaders(List<SpriteSource.Loader> loaders) {
		if (closing || loadersObserved) return loaders;
		loadersObserved = true;
		// Includes the supplier/list wrapper and queued task, supplier, and stream
		// observation owners. Workers reuse these claims; there is no per-event log queue.
		loaderReservation = budget.tryReserve(128L + 256L * loaders.size());
		if (loaderReservation == null) {
			PackForge.LOGGER.info("PackForge atlas phase: reload={} atlas={} stage=supplier_details_skipped reason=memory_budget", reloadId, atlas);
			return loaders;
		}
		ownerLimit = 16L + 3L * loaders.size();
		return new AbstractList<>() {
			@Override public int size() { return loaders.size(); }
			@Override public SpriteSource.Loader get(int index) {
				SpriteSource.Loader original = loaders.get(index);
				synchronized (AtlasDiagnostics.this) {
					if (closing) return original;
				}
				return resourceLoader -> {
					Owner owner = retain();
					if (owner == null) return original.get(resourceLoader);
					try (owner) {
						Sample sample = start();
						SpriteContents result = null;
						try { result = original.get(resourceLoader); return result; }
						finally { recordSupplier(result == null ? "loader_index_" + index : result.name().toString(), sample); }
					}
				};
			}
		};
	}

	private synchronized void recordSupplier(String id, Sample sample) {
		if (closing) return;
		long elapsed = System.nanoTime() - sample.wallNs();
		supplierCount++;
		if (elapsed > slowestSupplierNs) {
			long cpu = cpuNow();
			slowestSupplierNs = elapsed;
			slowestSupplierCpuNs = sample.cpuNs() < 0 || cpu < 0 ? -1 : cpu - sample.cpuNs();
			slowestSupplier = bounded(id);
		}
	}

	public static Sample start() { return new Sample(System.nanoTime(), cpuNow()); }

	public synchronized void stage(String stage, Sample sample, Throwable failure) {
		if (closing) return;
		long cpu = cpuNow();
		PackForge.LOGGER.info("PackForge atlas phase: reload={} atlas={} stage={} wall_us={} cpu_us={} outcome={}",
			reloadId, atlas, stage, (System.nanoTime() - sample.wallNs()) / 1000,
			sample.cpuNs() < 0 || cpu < 0 ? -1 : (cpu - sample.cpuNs()) / 1000,
			failure == null ? "ok" : failure.getClass().getSimpleName());
	}

	/** Completion may run on another thread, so asynchronous intervals have no thread CPU value. */
	public void wallStage(String stage, long startNs, Throwable failure) {
		stage(stage, new Sample(startNs, -1), failure);
	}

	public synchronized SpriteResourceLoader wrap(SpriteResourceLoader delegate) {
		if (closing) return delegate;
		return DiagnosticSpriteResourceLoader.wrap(delegate, this);
	}

	public SpriteContents observeSprite(Identifier id, Supplier<SpriteContents> load) {
		Owner owner = retain();
		if (owner == null) return load.get();
		try (owner) {
			Sample sample = start();
			ReadProbe previous = READING.get();
			ReadProbe probe = new ReadProbe();
			READING.set(probe);
			try {
				return load.get();
			} finally {
				if (previous == null) READING.remove();
				else READING.set(previous);
				recordSprite(id.toString(), sample, probe);
			}
		}
	}

	private synchronized void recordSprite(String id, Sample sample, ReadProbe probe) {
		if (closing) return;
		long elapsed = System.nanoTime() - sample.wallNs();
		long cpu = cpuNow();
		long cpuElapsed = sample.cpuNs() < 0 || cpu < 0 ? -1 : cpu - sample.cpuNs();
		spriteCount++;
		spriteWallNs += elapsed;
		if (cpuElapsed >= 0) spriteCpuNs += cpuElapsed;
		else spriteCpuAvailable = false;
		spriteReadNs += probe.readNs;
		spriteReadBytes += probe.bytes;
		if (elapsed > slowestNs) {
			slowestNs = elapsed;
			slowestCpuNs = cpuElapsed;
			slowestReadNs = probe.readNs;
			slowestSprite = bounded(id);
		}
	}

	public synchronized void decodeFinished(long startNs, Throwable failure) {
		if (closing) return;
		wallStage("decode", startNs, failure);
		PackForge.LOGGER.info("PackForge atlas sprites: reload={} atlas={} resource_loads={} summed_wall_us={} summed_cpu_us={} stream_read_us={} stream_bytes={} slowest={} slowest_wall_us={} slowest_cpu_us={} slowest_read_us={}",
			reloadId, atlas, spriteCount, spriteWallNs / 1000, spriteCpuAvailable ? spriteCpuNs / 1000 : -1, spriteReadNs / 1000, spriteReadBytes,
			slowestSprite, slowestNs / 1000, slowestCpuNs < 0 ? -1 : slowestCpuNs / 1000, slowestReadNs / 1000);
		PackForge.LOGGER.info("PackForge atlas suppliers: reload={} atlas={} suppliers={} slowest={} slowest_wall_us={} slowest_cpu_us={}",
			reloadId, atlas, supplierCount, slowestSupplier, slowestSupplierNs / 1000,
			slowestSupplierCpuNs < 0 ? -1 : slowestSupplierCpuNs / 1000);
	}

	public static InputStream observeStream(InputStream input, long openStarted) {
		ReadProbe probe = READING.get();
		if (probe == null) return input;
		probe.readNs += System.nanoTime() - openStarted;
		return new FilterInputStream(input) {
			@Override public int read() throws IOException {
				long start = System.nanoTime();
				try { int value = in.read(); if (value >= 0) probe.bytes++; return value; }
				finally { probe.readNs += System.nanoTime() - start; }
			}
			@Override public int read(byte[] bytes, int offset, int length) throws IOException {
				long start = System.nanoTime();
				try { int count = in.read(bytes, offset, length); if (count > 0) probe.bytes += count; return count; }
				finally { probe.readNs += System.nanoTime() - start; }
			}
		};
	}

	public static boolean observingRead() { return READING.get() != null; }

	private static long cpuNow() {
		return THREADS.isCurrentThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled()
			? THREADS.getCurrentThreadCpuTime() : -1;
	}

	private static String bounded(String value) { return value.length() <= 256 ? value : value.substring(0, 256); }

	/** Claim before submission: a cancelled outer future does not mean queued or active work is gone. */
	public Executor bind(Executor executor) {
		return command -> {
			Owner owner = retain();
			if (owner == null) {
				executor.execute(command);
				return;
			}
			try {
				executor.execute(() -> {
					try { command.run(); }
					finally { owner.close(); }
				});
			} catch (RuntimeException | Error failure) {
				owner.close();
				throw failure;
			}
		};
	}

	private synchronized Owner retain() {
		if (closing || owners >= ownerLimit) return null;
		Owner owner = new Owner();
		owners++;
		return owner;
	}

	@Override public synchronized void close() {
		closing = true;
		releaseIfUnused();
	}

	private void releaseIfUnused() {
		if (closing && owners == 0) {
			if (loaderReservation != null) loaderReservation.close();
			reservation.close();
		}
	}

	private final class Owner implements AutoCloseable {
		private boolean released;
		@Override public void close() {
			synchronized (AtlasDiagnostics.this) {
				if (released) return;
				released = true;
				owners--;
				releaseIfUnused();
			}
		}
	}

	synchronized Snapshot snapshot() { return new Snapshot(reloadId, atlas, spriteCount, spriteReadBytes, slowestSprite); }

	public record Sample(long wallNs, long cpuNs) {}
	record Snapshot(long reloadId, String atlas, long spriteCount, long readBytes, String slowestSprite) {}
	private static final class ReadProbe { private long readNs; private long bytes; }
}
