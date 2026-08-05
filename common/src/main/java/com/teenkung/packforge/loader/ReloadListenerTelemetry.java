package com.teenkung.packforge.loader;

import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Reload listener boundaries. Detailed task telemetry remains conditional,
 * but every supplied listener executor gets one lightweight context-binding
 * runnable so queued work keeps its reload snapshot when a newer reload starts.
 */
public final class ReloadListenerTelemetry {
	public static Runnable prepare(String listenerName, Runnable command) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null
			? command
			: ReloadExecutionContext.bindRunnable(context, prepare(context, canonicalName(listenerName), command));
	}

	public static Runnable apply(String listenerName, Runnable command) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null
			? command
			: ReloadExecutionContext.bindRunnable(context, apply(context, canonicalName(listenerName), command));
	}

	static Runnable prepare(String listenerName, Runnable command, boolean timings, boolean status) {
		Objects.requireNonNull(command, "command");
		if (!timings && !status) {
			return command;
		}
		ReloadExecutionContext context = ReloadExecutionContext.current();
		String canonical = canonicalName(listenerName);
		return () -> {
			if (status) {
				ReloadStatus.prepareStarted(context, canonical);
			}
			long startNs = timings ? System.nanoTime() : 0L;
			try {
				command.run();
			} finally {
				if (timings) {
					LoaderTimings.recordListenerPrepare(context, canonical, System.nanoTime() - startNs);
				}
				if (status) {
					ReloadStatus.prepareFinished(context);
				}
			}
		};
	}

	static Runnable apply(String listenerName, Runnable command, boolean timings, boolean status) {
		Objects.requireNonNull(command, "command");
		if (!timings && !status) {
			return command;
		}
		ReloadExecutionContext context = ReloadExecutionContext.current();
		String canonical = canonicalName(listenerName);
		return () -> {
			if (status) {
				ReloadStatus.applyStarted(context, canonical);
			}
			long startNs = timings ? System.nanoTime() : 0L;
			boolean successful = false;
			try {
				command.run();
				successful = true;
			} finally {
				if (timings) {
					LoaderTimings.recordListenerApply(context, canonical, System.nanoTime() - startNs);
				}
				if (successful && status) {
					ReloadStatus.resourceApplied(context, canonical);
				}
				if (status) {
					ReloadStatus.applyFinished(context);
				}
			}
		};
	}

	public static Executor prepareExecutor(ReloadExecutionContext context, String listenerName, Executor original) {
		Objects.requireNonNull(original, "original");
		if (context == null) {
			return original;
		}
		String canonical = canonicalName(listenerName);
		boolean detailed = context.features().taskExecutorWrappingEnabled();
		return command -> {
			Runnable task = detailed ? prepare(context, canonical, command) : command;
			original.execute(ReloadExecutionContext.bindRunnable(context, task));
		};
	}

	public static Executor applyExecutor(ReloadExecutionContext context, String listenerName, Executor original) {
		Objects.requireNonNull(original, "original");
		if (context == null) {
			return original;
		}
		String canonical = canonicalName(listenerName);
		boolean detailed = context.features().taskExecutorWrappingEnabled();
		return command -> {
			Runnable task = detailed ? apply(context, canonical, command) : command;
			original.execute(ReloadExecutionContext.bindRunnable(context, task));
		};
	}

	static Runnable prepare(ReloadExecutionContext context, String listenerName, Runnable command) {
		Objects.requireNonNull(command, "command");
		if (context == null) {
			return command;
		}
		var features = context.features();
		boolean timings = features.reloadListenerTimingsEnabled();
		boolean startupTiming = features.startupTimingActiveAtStart();
		boolean status = features.statusTrackingEnabled();
		if (!timings && !startupTiming && !status) {
			return command;
		}
		String canonical = canonicalName(listenerName);
		return () -> {
			if (status && features.loadingStatusOverlayEnabled()) {
				ReloadStatus.prepareStarted(context, canonical);
			}
			if (features.startupStatusOverlayEnabled() && features.startupTimingActiveAtStart()) {
				StartupStatus.update("Preparing", readableStartupName(canonical));
			}
			long startNs = timings || startupTiming ? System.nanoTime() : 0L;
			try {
				command.run();
			} finally {
				if (timings) {
					LoaderTimings.recordListenerPrepare(context, canonical, System.nanoTime() - startNs);
				}
				if (startupTiming) {
					StartupTimings.recordDuration("prepare " + readableStartupName(canonical), System.nanoTime() - startNs);
				}
				if (status && features.loadingStatusOverlayEnabled()) {
					ReloadStatus.prepareFinished(context);
				}
			}
		};
	}

	static Runnable apply(ReloadExecutionContext context, String listenerName, Runnable command) {
		Objects.requireNonNull(command, "command");
		if (context == null) {
			return command;
		}
		var features = context.features();
		boolean timings = features.reloadListenerTimingsEnabled();
		boolean startupTiming = features.startupTimingActiveAtStart();
		boolean status = features.statusTrackingEnabled();
		if (!timings && !startupTiming && !status) {
			return command;
		}
		String canonical = canonicalName(listenerName);
		return () -> {
			if (status && features.loadingStatusOverlayEnabled()) {
				ReloadStatus.applyStarted(context, canonical);
			}
			if (features.startupStatusOverlayEnabled() && features.startupTimingActiveAtStart()) {
				StartupStatus.update("Applying", readableStartupName(canonical));
			}
			long startNs = timings || startupTiming ? System.nanoTime() : 0L;
			boolean successful = false;
			try {
				command.run();
				successful = true;
			} finally {
				if (timings) {
					LoaderTimings.recordListenerApply(context, canonical, System.nanoTime() - startNs);
				}
				if (startupTiming) {
					StartupTimings.recordDuration("apply " + readableStartupName(canonical), System.nanoTime() - startNs);
				}
				if (successful && status) {
					ReloadStatus.resourceApplied(context, canonical);
				}
				if (status && features.loadingStatusOverlayEnabled()) {
					ReloadStatus.applyFinished(context);
				}
			}
		};
	}

	/**
	 * Observes one StateFactory future without replacing it.  This keeps the
	 * default path at listener granularity and leaves task submission entirely
	 * under Minecraft's original executors.
	 */
	public static CompletableFuture<?> observeListenerFuture(
		ReloadExecutionContext context,
		String listenerName,
		CompletableFuture<?> future,
		long startedNs
	) {
		if (context == null || future == null) {
			return future;
		}
		var features = context.features();
		String canonical = canonicalName(listenerName);
		boolean detailed = features.taskExecutorWrappingEnabled();
		boolean status = features.statusTrackingEnabled();
		if (startedNs == 0L && (detailed || !status)) {
			return future;
		}
		if (!detailed && status) {
			ReloadStatus.listenerStarted(context, canonical);
			if (features.startupStatusOverlayEnabled() && features.startupTimingActiveAtStart()) {
				StartupStatus.update("Loading", readableStartupName(canonical));
			}
		}
		future.whenComplete((ignored, error) -> {
			if (startedNs != 0L) {
				LoaderTimings.recordListenerWall(context, canonical, System.nanoTime() - startedNs);
			}
			if (!detailed && status) {
				if (error == null) {
					ReloadStatus.resourceApplied(context, canonical);
				}
				ReloadStatus.listenerFinished(context);
			}
		});
		return future;
	}

	public static String canonicalName(String listenerName) {
		if (listenerName == null || listenerName.isBlank()) {
			return "resources";
		}
		return listenerName.trim();
	}

	private static String readableStartupName(String listenerName) {
		return switch (listenerName) {
			case "AtlasManager" -> "texture atlases";
			case "ModelManager" -> "models";
			case "TextureManager" -> "textures";
			case "SoundManager" -> "sounds";
			case "LanguageManager" -> "languages";
			case "FontManager" -> "fonts";
			case "BlockColors" -> "block colors";
			case "ItemColors" -> "item colors";
			default -> listenerName;
		};
	}

	private ReloadListenerTelemetry() {}
}
