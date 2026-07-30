package com.teenkung.packforge.loader;

final class InitialUiResourceReadiness {
	private volatile boolean shaderReady;
	private volatile boolean fontReady;

	void listenerApplied(String listenerName) {
		String canonical = canonical(listenerName);
		if (canonical.contains("shaderloader") || canonical.endsWith("shader") || canonical.contains("shadermanager")) {
			this.shaderReady = true;
		} else if (canonical.contains("fontmanager") || canonical.contains("fontloader") || canonical.endsWith("font")) {
			this.fontReady = true;
		}
	}

	void reset() {
		this.shaderReady = false;
		this.fontReady = false;
	}

	boolean isReady() {
		return this.shaderReady && this.fontReady;
	}

	private static String canonical(String listenerName) {
		if (listenerName == null) return "";
		StringBuilder result = new StringBuilder(listenerName.length());
		for (int index = 0; index < listenerName.length(); index++) {
			char character = listenerName.charAt(index);
			if (Character.isLetterOrDigit(character)) result.append(Character.toLowerCase(character));
		}
		String value = result.toString();
		return value.replace("reloadlistener", "").replace("resources", "");
	}
}
