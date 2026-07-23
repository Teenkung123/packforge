package com.teenkung.packforge.loader;

final class InitialUiResourceReadiness {
	private volatile boolean shaderReady;
	private volatile boolean fontReady;

	void listenerApplied(String listenerName) {
		if (matchesListener(listenerName, "Shader Loader")) {
			this.shaderReady = true;
		} else if (matchesListener(listenerName, "FontManager")) {
			this.fontReady = true;
		}
	}

	boolean isReady() {
		return this.shaderReady && this.fontReady;
	}

	private static boolean matchesListener(String listenerName, String expectedName) {
		return expectedName.equals(listenerName)
			|| listenerName != null && listenerName.endsWith("(" + expectedName + ")");
	}
}
