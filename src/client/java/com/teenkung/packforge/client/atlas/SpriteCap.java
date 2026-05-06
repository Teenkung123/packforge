package com.teenkung.packforge.client.atlas;

public final class SpriteCap {
	public static int computeScale(int frameWidth, int frameHeight, int capPx) {
		if (frameWidth <= capPx && frameHeight <= capPx) return 1;
		int s = 1;
		while (true) {
			int next = s * 2;
			if (frameWidth % next != 0 || frameHeight % next != 0) break;
			int nw = frameWidth / next;
			int nh = frameHeight / next;
			if (nw < 1 || nh < 1) break;
			s = next;
			if (frameWidth / s <= capPx && frameHeight / s <= capPx) break;
		}
		if (s == 1) return 1;
		if (frameWidth / s > capPx || frameHeight / s > capPx) return 1;
		return s;
	}

	private SpriteCap() {}
}
