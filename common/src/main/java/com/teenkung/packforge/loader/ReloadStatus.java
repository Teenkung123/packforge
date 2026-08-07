package com.teenkung.packforge.loader;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Reload UI state backed by the exact context that owns the work. */
public final class ReloadStatus {
	private static final AtomicReference<ReloadSummary> pendingSummary = new AtomicReference<>();
	private static final InitialUiResourceReadiness initialUiResources = new InitialUiResourceReadiness();

	public static void start() {
		ReloadExecutionContext context = ReloadExecutionContext.start(
			System.nanoTime(),
			com.teenkung.packforge.config.ReloadFeatureSnapshot.capture()
		);
		start(context);
	}

	public static void start(ReloadExecutionContext context) {
		if (context == null) {
			return;
		}
		context.metrics().beginStatus();
		if (ReloadExecutionContext.isCurrent(context)) {
			pendingSummary.set(null);
		}
	}

	public static void finish(Throwable error) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context != null) {
			finish(context, error, context.features().reloadSummaryToastEnabled());
		}
	}

	static void finish(Throwable error, boolean summaryToastEnabled) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context != null) {
			finish(context, error, summaryToastEnabled);
		}
	}

	public static void finish(ReloadExecutionContext context, Throwable error) {
		if (context != null) {
			finish(context, error, context.features().reloadSummaryToastEnabled());
		}
	}

	static void finish(ReloadExecutionContext context, Throwable error, boolean summaryToastEnabled) {
		if (context == null) {
			return;
		}
		boolean current = ReloadExecutionContext.isCurrent(context);
		if (current && summaryToastEnabled) {
			pendingSummary.set(new ReloadSummary(
				context.metrics().elapsedNs() / 1_000_000L,
				error == null
			));
		}
		context.metrics().finishStatus(
			error == null ? "Finishing" : "Failed",
			error == null ? "applying resources" : "resource reload"
		);
		ReloadExecutionContext.finish(context);
	}

	public static void prepareStarted(String listenerName) {
		prepareStarted(ReloadExecutionContext.current(), listenerName);
	}

	static void prepareStarted(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().prepareStarted(readableListener(listenerName));
		}
	}

	public static void prepareFinished() {
		prepareFinished(ReloadExecutionContext.current());
	}

	static void prepareFinished(ReloadExecutionContext context) {
		if (context != null) {
			context.metrics().prepareFinished();
		}
	}

	public static void applyStarted(String listenerName) {
		applyStarted(ReloadExecutionContext.current(), listenerName);
	}

	static void applyStarted(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().applyStarted(readableListener(listenerName));
		}
	}

	public static void applyFinished() {
		applyFinished(ReloadExecutionContext.current());
	}

	static void applyFinished(ReloadExecutionContext context) {
		if (context != null) {
			context.metrics().applyFinished();
		}
	}

	static void listenerStarted(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().listenerStarted(readableListener(listenerName));
		}
	}

	static void listenerFinished(ReloadExecutionContext context) {
		if (context != null) {
			context.metrics().listenerFinished();
		}
	}

	static void resourceApplied(ReloadExecutionContext context, String listenerName) {
		if (context != null && ReloadExecutionContext.isCurrent(context)) {
			initialUiResources.listenerApplied(listenerName);
		}
	}

	public static void resourceApplied(String listenerName) {
		resourceApplied(ReloadExecutionContext.current(), listenerName);
	}

	public static boolean isStatusTextReady() {
		return initialUiResources.isReady();
	}

	public static boolean isActive() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context != null && context.metrics().isActive();
	}

	public static boolean isComplete() {
		ReloadExecutionContext context = ReloadExecutionContext.visible();
		return context != null && context.metrics().isComplete();
	}

	public static String line(float progress) {
		int percent = Math.max(0, Math.min(100, Math.round(progress * 100.0f)));
		ReloadExecutionContext context = ReloadExecutionContext.visible();
		long elapsedMs = context == null ? 0L : context.metrics().elapsedNs() / 1_000_000L;
		return "Loading resources - " + percent + "% - " + elapsedMs + "ms";
	}

	public static float displayProgress(float progress) {
		float normalized = Math.max(0.0F, Math.min(1.0F, progress));
		return isComplete() ? 1.0F : Math.min(0.99F, normalized);
	}

	public static String detailLine() {
		ReloadExecutionContext context = ReloadExecutionContext.visible();
		if (context == null) {
			return "Starting resource reload";
		}
		ReloadMetrics metrics = context.metrics();
		String currentPhase = metrics.phase();
		String currentDetail = metrics.detail();
		int prepare = metrics.activePrepareTasks();
		int apply = metrics.activeApplyTasks();
		int listeners = metrics.activeListeners();
		if (prepare > 1 && "Preparing".equals(currentPhase)) {
			return currentPhase + " " + currentDetail + " (" + prepare + " tasks)";
		}
		if (apply > 1 && "Applying".equals(currentPhase)) {
			return currentPhase + " " + currentDetail + " (" + apply + " tasks)";
		}
		if (listeners > 1 && "Loading".equals(currentPhase)) {
			return currentPhase + " " + currentDetail + " (" + listeners + " listeners)";
		}
		return currentPhase + " " + currentDetail;
	}

	public static ReloadSummary consumeSummaryToast() {
		return pendingSummary.getAndSet(null);
	}

	static boolean statusTrackingEnabled(ReloadExecutionContext context) {
		return context != null && context.features().statusTrackingEnabled();
	}

	static void resetForTesting() {
		ReloadExecutionContext.resetForTesting();
		pendingSummary.set(null);
		initialUiResources.reset();
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
