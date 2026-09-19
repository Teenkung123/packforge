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
		if (current && error == null) {
			initialUiResources.reloadSucceeded();
		}
		ReloadExecutionContext.finish(context);
	}

	public static void prepareStarted(String listenerName) {
		prepareStarted(ReloadExecutionContext.current(), listenerName);
	}

	static void prepareStarted(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().prepareStarted(context.metrics().readableListener(listenerName));
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

	static void prepareFinished(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().prepareFinished(context.metrics().readableListener(listenerName));
		}
	}

	public static void applyStarted(String listenerName) {
		applyStarted(ReloadExecutionContext.current(), listenerName);
	}

	static void applyStarted(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().applyStarted(context.metrics().readableListener(listenerName));
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

	static void applyFinished(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().applyFinished(context.metrics().readableListener(listenerName));
		}
	}

	static void listenerStarted(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().listenerStarted(context.metrics().readableListener(listenerName));
		}
	}

	static void listenerFinished(ReloadExecutionContext context) {
		if (context != null) {
			context.metrics().listenerFinished();
		}
	}

	static void listenerFinished(ReloadExecutionContext context, String listenerName) {
		if (context != null) {
			context.metrics().listenerFinished(context.metrics().readableListener(listenerName));
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

	/** Feedback fallback is allowed only for a requested overlay displaced by an external owner. */
	public static boolean externalFeedbackEnabled() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context != null && context.features().externalFeedbackEnabled();
	}

	public static boolean isComplete() {
		ReloadExecutionContext context = ReloadExecutionContext.visible();
		return context != null && context.metrics().isComplete();
	}

	public static String line(float progress) {
		ReloadExecutionContext context = ReloadExecutionContext.visible();
		long elapsedMs = context == null ? 0L : context.metrics().elapsedNs() / 1_000_000L;
		String elapsed = elapsedMs / 1_000L + "." + (elapsedMs % 1_000L) / 100L + "s";
		if (context == null) return "Loading resources - " + elapsed;
		ReloadMetrics.StatusSnapshot snapshot = context.metrics().statusSnapshot();
		String completion;
		if (snapshot.complete()) {
			completion = "Failed".equals(snapshot.phase()) ? "failed" : "complete";
		} else {
			completion = snapshot.completedListeners() + (snapshot.completedListeners() == 1 ? " stage complete" : " stages complete");
		}
		return "Loading resources - " + completion + " - " + elapsed;
	}

	/** Retained for older adapters; an estimate cannot reach 100 before completion. */
	public static float displayProgress(float progress) {
		float normalized = Math.max(0.0F, Math.min(1.0F, progress));
		return isComplete() ? 1.0F : Math.min(0.99F, normalized);
	}

	public static String detailLine() {
		ReloadExecutionContext context = ReloadExecutionContext.visible();
		if (context == null) return "Starting resource reload";
		ReloadMetrics.StatusSnapshot snapshot = context.metrics().statusSnapshot();
		String detail = snapshot.phase() + " " + snapshot.detail();
		if (snapshot.activeCount() > 1) {
			detail += " (" + snapshot.activeCount() + " active)";
		}
		return detail;
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

	static String readableListener(String listenerName) {
		if (listenerName == null || listenerName.isBlank()) {
			return "resources";
		}
		// Fabric may append a debug label to vanilla listener identifiers.
		int separator = listenerName.indexOf(' ');
		String identifier = separator < 0 ? listenerName : listenerName.substring(0, separator);
		switch (identifier) {
			case "minecraft:languages": return "languages";
			case "minecraft:textures": return "textures";
			case "minecraft:models": return "models";
			case "minecraft:sounds": return "sounds";
			case "minecraft:fonts": return "fonts";
		}
		String simpleIdentifier = identifier;
		int dot = simpleIdentifier.lastIndexOf('.');
		if (dot >= 0 && dot + 1 < simpleIdentifier.length()) {
			simpleIdentifier = simpleIdentifier.substring(dot + 1);
		}
		switch (simpleIdentifier) {
			case "class_378": return "fonts";
			case "class_1076": return "languages";
			case "class_1060": return "textures";
			case "class_1144": return "sounds";
			case "class_1092": return "models";
			case "class_324": return "block colors";
			case "class_325": return "item colors";
			case "class_1069": return "grass colors";
			case "class_1070": return "foliage colors";
			case "class_10831": return "dry foliage colors";
			case "class_4044": return "painting textures";
			case "class_4074": return "mob-effect textures";
			case "class_1071": return "skins";
			case "class_4008": return "splash text";
			case "class_1142": return "music manager";
			case "class_6877": return "periodic notifications";
			case "class_5407": return "GPU warnlist";
			case "class_5599": return "entity models";
			case "class_824": return "block entity dispatcher";
			case "class_756": return "block entity renderer";
			case "class_776": return "block renderer";
			case "class_918": return "item renderer";
			case "class_898": return "entity renderer";
			case "class_4599": return "render buffers";
			case "class_1124": return "search registry";
			case "class_702": return "particles";
			case "class_8658": return "GUI sprites";
			case "class_9443": return "map decorations";
			case "class_9955": return "clouds";
			case "class_10201": return "equipment assets";
			case "class_11327": return "waypoint styles";
			case "class_761": return "level renderer";
			case "class_757", "class_10151", "GameRenderer", "ShaderManager": return "shader loader";
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
