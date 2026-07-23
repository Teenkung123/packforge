package com.teenkung.packforge.loader;

import com.teenkung.packforge.config.FeatureFlags;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReloadStatus {
	private static final AtomicInteger activePrepareTasks = new AtomicInteger();
	private static final AtomicInteger activeApplyTasks = new AtomicInteger();
	private static final InitialUiResourceReadiness initialUiResources = new InitialUiResourceReadiness();
	private static volatile boolean active;
	private static volatile boolean complete;
	private static volatile long startNs;
	private static volatile ReloadSummary pendingSummary;
	private static volatile String phase = "Starting";
	private static volatile String detail = "resource reload";

	public static void start() {
		active = true;
		complete = false;
		startNs = System.nanoTime();
		pendingSummary = null;
		activePrepareTasks.set(0);
		activeApplyTasks.set(0);
		phase = "Starting";
		detail = "resource reload";
	}

	public static void finish(Throwable error) {
		complete = true;
		long elapsedMs = startNs == 0L ? 0L : (System.nanoTime() - startNs) / 1_000_000L;
		if (FeatureFlags.reloadSummaryToastEnabled()) {
			pendingSummary = new ReloadSummary(elapsedMs, error == null);
		}
		phase = error == null ? "Finishing" : "Failed";
		detail = error == null ? "applying resources" : "resource reload";
		activePrepareTasks.set(0);
		activeApplyTasks.set(0);
	}

	public static void prepareStarted(String listenerName) {
		activePrepareTasks.incrementAndGet();
		phase = "Preparing";
		detail = readableListener(listenerName);
	}

	public static void prepareFinished() {
		activePrepareTasks.updateAndGet(value -> Math.max(0, value - 1));
	}

	public static void applyStarted(String listenerName) {
		activeApplyTasks.incrementAndGet();
		phase = "Applying";
		detail = readableListener(listenerName);
	}

	public static void applyFinished() {
		activeApplyTasks.updateAndGet(value -> Math.max(0, value - 1));
	}

	public static void resourceApplied(String listenerName) {
		initialUiResources.listenerApplied(listenerName);
	}

	public static boolean isStatusTextReady() {
		return initialUiResources.isReady();
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean isComplete() {
		return complete;
	}

	public static String line(float progress) {
		int percent = Math.max(0, Math.min(100, Math.round(progress * 100.0f)));
		long elapsedMs = startNs == 0L ? 0L : (System.nanoTime() - startNs) / 1_000_000L;
		return "Loading resources - " + percent + "% - " + elapsedMs + "ms";
	}

	public static String detailLine() {
		String currentPhase = phase;
		String currentDetail = detail;
		int prepare = activePrepareTasks.get();
		int apply = activeApplyTasks.get();
		if (prepare > 1 && "Preparing".equals(currentPhase)) {
			return currentPhase + " " + currentDetail + " (" + prepare + " tasks)";
		}
		if (apply > 1 && "Applying".equals(currentPhase)) {
			return currentPhase + " " + currentDetail + " (" + apply + " tasks)";
		}
		return currentPhase + " " + currentDetail;
	}

	public static ReloadSummary consumeSummaryToast() {
		ReloadSummary summary = pendingSummary;
		pendingSummary = null;
		return summary;
	}

	private static String readableListener(String listenerName) {
		if (listenerName == null || listenerName.isBlank()) {
			return "resources";
		}
		return switch (listenerName) {
			case "AtlasManager" -> "texture atlases";
			case "ModelManager" -> "models";
			case "TextureManager" -> "textures";
			case "SoundManager" -> "sounds";
			case "LanguageManager" -> "languages";
			case "FontManager" -> "fonts";
			case "BlockColors" -> "block colors";
			case "ItemColors" -> "item colors";
			case "Shader Loader" -> "shader loader";
			case "GpuWarnlistManager" -> "GPU warnlist";
			case "SplashManager" -> "splash text";
			case "WaypointStyleManager" -> "waypoint styles";
			default -> humanize(listenerName);
		};
	}

	private static String humanize(String name) {
		String simple = name;
		int dot = simple.lastIndexOf('.');
		if (dot >= 0 && dot + 1 < simple.length()) {
			simple = simple.substring(dot + 1);
		}
		simple = simple.replace('$', ' ');
		simple = simple.replaceAll("([a-z])([A-Z])", "$1 $2");
		simple = simple.replaceAll("(?i) reload listener", "");
		simple = simple.replaceAll("(?i) manager", "");
		simple = simple.trim();
		if (simple.isEmpty()) {
			return "resources";
		}
		return simple.toLowerCase(Locale.ROOT);
	}

	private ReloadStatus() {}

	public record ReloadSummary(long elapsedMs, boolean success) {
	}
}
