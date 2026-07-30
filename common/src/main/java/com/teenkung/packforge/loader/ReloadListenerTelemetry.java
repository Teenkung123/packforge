package com.teenkung.packforge.loader;

import com.teenkung.packforge.config.FeatureFlags;

import java.util.Objects;

/**
 * Loader-neutral wrappers for the runnable work scheduled by a reload listener.
 * Version adapters decide where the executors are supplied; this class keeps the
 * accounting and success semantics identical across those adapters.
 */
public final class ReloadListenerTelemetry {
	public static Runnable prepare(String listenerName, Runnable command) {
		return prepare(listenerName, command, FeatureFlags.reloadListenerTimingsEnabled(), statusTrackingEnabled());
	}

	public static Runnable apply(String listenerName, Runnable command) {
		return apply(listenerName, command, FeatureFlags.reloadListenerTimingsEnabled(), statusTrackingEnabled());
	}

	static Runnable prepare(String listenerName, Runnable command, boolean timings, boolean status) {
		Objects.requireNonNull(command, "command");
		if (!timings && !status) {
			return command;
		}
		return () -> {
			if (status) ReloadStatus.prepareStarted(listenerName);
			long startNs = timings ? System.nanoTime() : 0L;
			try {
				command.run();
			} finally {
				if (timings) LoaderTimings.recordListenerPrepare(listenerName, System.nanoTime() - startNs);
				if (status) ReloadStatus.prepareFinished();
			}
		};
	}

	static Runnable apply(String listenerName, Runnable command, boolean timings, boolean status) {
		Objects.requireNonNull(command, "command");
		if (!timings && !status) {
			return command;
		}
		return () -> {
			if (status) ReloadStatus.applyStarted(listenerName);
			long startNs = timings ? System.nanoTime() : 0L;
			boolean successful = false;
			try {
				command.run();
				successful = true;
			} finally {
				if (timings) LoaderTimings.recordListenerApply(listenerName, System.nanoTime() - startNs);
				if (successful && status) ReloadStatus.resourceApplied(listenerName);
				if (status) ReloadStatus.applyFinished();
			}
		};
	}

	private static boolean statusTrackingEnabled() {
		return FeatureFlags.loadingStatusOverlayEnabled()
			|| FeatureFlags.startupStatusOverlayEnabled()
			|| FeatureFlags.reloadSummaryToastEnabled();
	}

	private ReloadListenerTelemetry() {}
}
