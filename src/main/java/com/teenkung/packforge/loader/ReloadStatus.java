package com.teenkung.packforge.loader;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReloadStatus {
	private static final AtomicInteger activePrepareTasks = new AtomicInteger();
	private static final AtomicInteger activeApplyTasks = new AtomicInteger();
	private static volatile boolean active;
	private static volatile boolean complete;
	private static volatile String phase = "Starting";
	private static volatile String detail = "resource reload";

	public static void start() {
		active = true;
		complete = false;
		activePrepareTasks.set(0);
		activeApplyTasks.set(0);
		phase = "Starting";
		detail = "resource reload";
	}

	public static void finish(Throwable error) {
		complete = true;
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

	public static boolean isActive() {
		return active;
	}

	public static boolean isComplete() {
		return complete;
	}

	public static String line(float progress) {
		int percent = Math.clamp(Math.round(progress * 100.0f), 0, 100);
		return "Loading resources - " + percent + "%";
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
}
