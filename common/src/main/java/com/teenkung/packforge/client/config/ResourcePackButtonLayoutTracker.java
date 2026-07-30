package com.teenkung.packforge.client.config;

/**
 * Tracks the resource-pack screen's widget layout so version adapters only
 * recompute PackForge's button position when another mod changes that layout.
 */
public final class ResourcePackButtonLayoutTracker {
	private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;

	private boolean initialized;
	private long previousSignature;

	public boolean shouldReflow(long currentSignature, boolean buttonCollides) {
		if (!this.initialized || this.previousSignature != currentSignature || buttonCollides) {
			this.initialized = true;
			this.previousSignature = currentSignature;
			return true;
		}
		return false;
	}

	public static long beginSignature(int screenWidth, int screenHeight) {
		long signature = mix(FNV_OFFSET_BASIS, screenWidth);
		return mix(signature, screenHeight);
	}

	public static long includeWidget(
		long signature,
		int identity,
		int x,
		int y,
		int width,
		int height,
		boolean visible
	) {
		long result = mix(signature, identity);
		result = mix(result, x);
		result = mix(result, y);
		result = mix(result, width);
		result = mix(result, height);
		return mix(result, visible ? 1 : 0);
	}

	private static long mix(long signature, int value) {
		return (signature ^ Integer.toUnsignedLong(value)) * FNV_PRIME;
	}
}
