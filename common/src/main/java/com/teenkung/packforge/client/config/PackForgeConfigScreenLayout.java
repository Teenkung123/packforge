package com.teenkung.packforge.client.config;

/**
 * Minecraft-independent layout policy shared by the paged legacy config
 * screens. The fixed header and footer bands stay clear of option rows even
 * when Minecraft selects a large GUI scale for a small window.
 */
public final class PackForgeConfigScreenLayout {
	private static final int CONTENT_TOP = 58;
	private static final int ROW_HEIGHT = 28;
	private static final int NAVIGATION_OFFSET = 60;
	private static final int ERROR_OFFSET = 86;
	private static final int MAXIMUM_PAGE_SIZE = 7;

	public static int pageSize(int screenHeight) {
		return pageSize(screenHeight, false);
	}

	public static int pageSize(int screenHeight, boolean reserveErrorBand) {
		int contentBottom = screenHeight - (reserveErrorBand ? ERROR_OFFSET : NAVIGATION_OFFSET);
		int availableHeight = Math.max(ROW_HEIGHT, contentBottom - CONTENT_TOP);
		return Math.max(1, Math.min(MAXIMUM_PAGE_SIZE, availableHeight / ROW_HEIGHT));
	}

	private PackForgeConfigScreenLayout() {}
}
