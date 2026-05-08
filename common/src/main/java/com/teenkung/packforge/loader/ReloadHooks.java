package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ReloadHooks {
	private static final List<Runnable> startHooks = new CopyOnWriteArrayList<>();

	public static void registerStartHook(Runnable r) {
		startHooks.add(r);
	}

	public static void fireStart() {
		for (Runnable r : startHooks) {
			try { r.run(); } catch (Throwable t) {
				PackForge.LOGGER.warn("PackForge reload start hook failed", t);
			}
		}
	}

	private ReloadHooks() {}
}
