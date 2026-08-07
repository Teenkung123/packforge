package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public final class ReloadHooks {
	private static final List<Runnable> startHooks = new CopyOnWriteArrayList<>();
	private static final List<BiConsumer<ReloadExecutionContext, Throwable>> completionHooks = new CopyOnWriteArrayList<>();

	public static void registerStartHook(Runnable r) {
		startHooks.add(r);
	}

	public static void registerCompletionHook(BiConsumer<ReloadExecutionContext, Throwable> hook) {
		completionHooks.add(hook);
	}

	public static void fireStart() {
		for (Runnable r : startHooks) {
			try { r.run(); } catch (Throwable t) {
				PackForge.LOGGER.warn("PackForge reload start hook failed", t);
			}
		}
	}

	public static void fireCompletion(ReloadExecutionContext context, Throwable error) {
		for (BiConsumer<ReloadExecutionContext, Throwable> hook : completionHooks) {
			try { hook.accept(context, error); } catch (Throwable t) {
				PackForge.LOGGER.warn("PackForge reload completion hook failed", t);
			}
		}
	}

	private ReloadHooks() {}
}
