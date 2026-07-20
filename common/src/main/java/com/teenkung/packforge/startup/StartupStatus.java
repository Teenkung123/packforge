package com.teenkung.packforge.startup;

public final class StartupStatus {
	private static volatile boolean active;
	private static volatile boolean complete;
	private static volatile long startNs;
	private static volatile String phase = "Starting";
	private static volatile String detail = "startup optimizer";

	public static void start() {
		active = true;
		complete = false;
		startNs = System.nanoTime();
		phase = "Starting";
		detail = "PackForge startup optimizer";
	}

	public static void update(String currentPhase, String currentDetail) {
		if (complete) {
			return;
		}
		active = true;
		phase = currentPhase;
		detail = currentDetail;
	}

	public static void finish() {
		complete = true;
		active = false;
		phase = "Ready";
		detail = "startup optimizer complete";
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean isComplete() {
		return complete;
	}

	public static String line() {
		long elapsedMs = startNs == 0L ? 0L : (System.nanoTime() - startNs) / 1_000_000L;
		return "Startup optimizer - " + elapsedMs + "ms";
	}

	public static String detailLine() {
		return phase + " " + detail;
	}

	private StartupStatus() {}
}
