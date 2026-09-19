package com.teenkung.packforge.loader;

import java.util.Locale;

final class InitialUiResourceReadiness {
	private volatile boolean shaderReady;
	private volatile boolean fontReady;

	void listenerApplied(String listenerName) {
		// These are the listener names emitted by the supported vanilla targets.
		// Resource paths such as "minecraft:shaders" and "minecraft:fonts" are
		// not listener identities and must not unlock the UI on their own.
		if (hasNamedListener(
			listenerName,
			"shader loader",
			"shaderloader",
			"shader manager",
			"shadermanager",
			"game renderer",
			"gamerenderer"
		)
			|| hasIntermediaryClass(listenerName, "757")
			|| hasIntermediaryClass(listenerName, "10151")) {
			this.shaderReady = true;
		} else if (hasNamedListener(listenerName, "font manager", "fontmanager", "font loader", "fontloader", "font_loader")
			|| hasIntermediaryClass(listenerName, "378")) {
			this.fontReady = true;
		}
	}

	void reset() {
		this.shaderReady = false;
		this.fontReady = false;
	}

	/**
	 * A successful complete resource reload is a loader-name-independent proof that the
	 * vanilla font and shader resources reached their apply phase.
	 */
	void reloadSucceeded() {
		this.shaderReady = true;
		this.fontReady = true;
	}

	boolean isReady() {
		return this.shaderReady && this.fontReady;
	}

	private static boolean hasNamedListener(String listenerName, String... labels) {
		if (listenerName == null) return false;
		String value = listenerName.toLowerCase(Locale.ROOT);
		for (String label : labels) {
			int offset = value.indexOf(label);
			while (offset >= 0) {
				int end = offset + label.length();
				boolean startsToken = offset == 0 || isListenerBoundary(value.charAt(offset - 1));
				boolean endsToken = end == value.length() || isListenerBoundary(value.charAt(end));
				if (startsToken && endsToken) return true;
				offset = value.indexOf(label, offset + 1);
			}
		}
		return false;
	}

	private static boolean isListenerBoundary(char character) {
		return !Character.isLetterOrDigit(character) && character != ':' && character != '/';
	}

	private static boolean hasIntermediaryClass(String listenerName, String classNumber) {
		if (listenerName == null || classNumber == null) return false;
		String value = listenerName.toLowerCase(Locale.ROOT);
		String marker = "class_" + classNumber;
		int offset = value.indexOf(marker);
		while (offset >= 0) {
			int end = offset + marker.length();
			boolean startsToken = offset == 0 || !Character.isLetterOrDigit(value.charAt(offset - 1));
			boolean endsToken = end == value.length() || !Character.isLetterOrDigit(value.charAt(end));
			if (startsToken && endsToken) return true;
			offset = value.indexOf(marker, offset + 1);
		}
		return false;
	}
}
