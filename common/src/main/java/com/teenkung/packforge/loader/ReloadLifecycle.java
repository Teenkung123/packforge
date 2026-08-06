package com.teenkung.packforge.loader;

/** Shared lifecycle boundary used by every version adapter. */
public final class ReloadLifecycle {
	public static ReloadExecutionContext startReload() {
		ReloadSessionTracker.ReloadSession session = ReloadSessionTracker.startReload();
		ReloadExecutionContext context = ReloadExecutionContext.start(session.id());
		LoaderTimings.onReloadStart(context);
		ReloadStatus.start(context);
		return context;
	}

	public static void finishReload(ReloadExecutionContext context, Throwable error) {
		if (context == null) {
			return;
		}
		LoaderTimings.onReloadEnd(context, error);
		LoaderTimings.onReloadComplete(context, error);
		ReloadHooks.fireCompletion(context, error);
		ReloadStatus.finish(context, error);
	}

	private ReloadLifecycle() {}
}
