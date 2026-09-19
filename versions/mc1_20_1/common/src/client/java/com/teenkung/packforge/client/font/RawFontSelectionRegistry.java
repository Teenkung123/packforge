package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.AllMissingGlyphProvider;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.client.gui.font.providers.UnihexProvider;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Reload-scoped registry for 1.20.1's raw, already-resolved provider lists. */
public final class RawFontSelectionRegistry {
	private static final Object LOCK = new Object();
	private static final Map<Object, Bundle> PREPARED = new IdentityHashMap<>();
	private static final ThreadLocal<Bundle> APPLYING = new ThreadLocal<>();
	private static final ThreadLocal<Session> PREPARING = new ThreadLocal<>();
	private static final ThreadLocal<RawFontPreparedSelection> RELOADING = new ThreadLocal<>();

	public static Session beginPreparation(ReloadExecutionContext context) {
		return new Session(context);
	}

	public static Session currentPreparation() {
		return PREPARING.get();
	}

	public static void store(Object preparation, Session session) {
		Bundle bundle = session.take();
		if (bundle == null) return;
		synchronized (LOCK) {
			PREPARED.put(preparation, bundle);
		}
		bundle.scope.onRetire(() -> discard(preparation, bundle));
	}

	public static void beginApply(Object preparation) {
		Bundle bundle;
		synchronized (LOCK) {
			bundle = PREPARED.remove(preparation);
		}
		if (bundle == null || bundle.closed || bundle.scope.isRetired()) {
			if (bundle != null) bundle.close();
			APPLYING.remove();
		} else {
			APPLYING.set(bundle);
		}
	}

	public static RawFontPreparedSelection beginReload(List<GlyphProvider> providers) {
		Bundle bundle = APPLYING.get();
		RawFontPreparedSelection selection = bundle == null || bundle.closed || bundle.scope.isRetired()
			? null : bundle.selections.get(StackKey.of(providers));
		if (selection == null || !selection.acquire()) {
			RELOADING.remove();
			return null;
		}
		RELOADING.set(selection);
		return selection;
	}

	public static RawFontPreparedSelection currentReload() {
		return RELOADING.get();
	}

	public static void endReload() {
		RawFontPreparedSelection selection = RELOADING.get();
		RELOADING.remove();
		if (selection != null) selection.releaseApplication();
	}

	public static void clear() {
		Bundle bundle = APPLYING.get();
		APPLYING.remove();
		if (bundle != null) bundle.close();
	}

	public static void resetForReload() {
		// A start hook for a newer reload must not discard a still-live prepared
		// bundle. Each exact bundle is removed by its own retirement callback.
		clear();
		PREPARING.remove();
		RELOADING.remove();
	}

	private static void discard(Object preparation, Bundle expected) {
		boolean removed;
		synchronized (LOCK) {
			removed = PREPARED.get(preparation) == expected;
			if (removed) PREPARED.remove(preparation);
		}
		// Retirement also reaches bundles already claimed by apply. Their active
		// FontSet invocation holds a temporary application pin until it returns.
		expected.close();
	}

	public static final class Session implements AutoCloseable {
		private final ReloadExecutionContext context;
		private final PreparationBudget.Scope scope;
		private final Map<StackKey, CompletableFuture<RawFontPreparedSelection>> selections = new LinkedHashMap<>();
		private boolean closed;

		private Session(ReloadExecutionContext context) {
			this.context = Objects.requireNonNull(context, "context");
			this.scope = context.preparationBudget();
		}

		public Executor executor(Executor supplied) {
			return command -> supplied.execute(() -> {
				Session previous = PREPARING.get();
				PREPARING.set(this);
				try (ReloadExecutionContext.Scope ignored = ReloadExecutionContext.bind(context)) {
					command.run();
				} finally {
					if (previous == null) PREPARING.remove(); else PREPARING.set(previous);
				}
			});
		}

		/** Replaces vanilla's warm-up pass only when the complete equivalent result is admitted. */
		public boolean finalizeProviders(List<GlyphProvider> raw, GlyphProvider fallback) {
			if (closed || scope.isRetired() || !known(raw) || !known(fallback)) return false;
			ArrayList<GlyphProvider> finalized = new ArrayList<>(raw.size() + 1);
			finalized.add(fallback);
			finalized.addAll(raw);
			finalized = reverse(finalized);
			StackKey key = StackKey.of(finalized);
			CompletableFuture<RawFontPreparedSelection> future;
			boolean owner = false;
			synchronized (this) {
				if (closed || scope.isRetired()) return false;
				future = selections.get(key);
				if (future == null) {
					future = new CompletableFuture<>();
					selections.put(key, future);
					owner = true;
				}
			}
			if (!owner) return adopt(raw, fallback, future);

			try {
				long advertised = advertised(finalized);
				if (advertised < 0) return reject(key, future);
				PreparationBudget.Reservation allocation = scope.tryReserve(
					RawFontPreparedSelection.requestedBytes(finalized.size(), advertised));
				if (allocation == null) return reject(key, future);
				RawFontPreparedSelection selection = RawFontPreparedSelection.compute(finalized, allocation);
				synchronized (this) {
					if (closed || scope.isRetired()) {
						selection.close();
						future.complete(null);
						return false;
					}
					future.complete(selection);
				}
				raw.add(0, fallback);
				return true;
			} catch (RuntimeException | Error failure) {
				reject(key, future);
				if (failure instanceof Error error) throw error;
				return false;
			}
		}

		private boolean adopt(List<GlyphProvider> raw, GlyphProvider fallback,
			CompletableFuture<RawFontPreparedSelection> future) {
			try {
				RawFontPreparedSelection selection = future.join();
				if (selection == null || !selection.available() || closed || scope.isRetired()) return false;
				raw.add(0, fallback);
				return true;
			} catch (RuntimeException ignored) {
				return false;
			}
		}

		private boolean reject(StackKey key, CompletableFuture<RawFontPreparedSelection> future) {
			future.complete(null);
			synchronized (this) {
				if (selections.get(key) == future) selections.remove(key);
			}
			return false;
		}

		private Bundle take() {
			synchronized (this) {
				if (closed || scope.isRetired()) {
					close();
					return null;
				}
				closed = true;
				Map<StackKey, RawFontPreparedSelection> completed = new LinkedHashMap<>();
				for (Map.Entry<StackKey, CompletableFuture<RawFontPreparedSelection>> entry : selections.entrySet()) {
					RawFontPreparedSelection selection = entry.getValue().getNow(null);
					if (selection != null) completed.put(entry.getKey(), selection);
				}
				return new Bundle(scope, completed);
			}
		}

		@Override public void close() {
			List<RawFontPreparedSelection> retired;
			synchronized (this) {
				if (closed) return;
				closed = true;
				retired = selections.values().stream()
					.map(future -> future.getNow(null)).filter(Objects::nonNull).toList();
				selections.values().forEach(future -> future.complete(null));
				selections.clear();
			}
			retired.forEach(RawFontPreparedSelection::close);
		}
	}

	private static boolean known(List<GlyphProvider> providers) {
		for (GlyphProvider provider : providers) if (!known(provider)) return false;
		return true;
	}

	private static boolean known(GlyphProvider provider) {
		Class<?> type = provider.getClass();
		return type == AllMissingGlyphProvider.class || type == SpaceProvider.class || type == BitmapProvider.class
			|| type == UnihexProvider.class || type == TrueTypeGlyphProvider.class;
	}

	private static long advertised(List<GlyphProvider> providers) {
		long total = 0L;
		for (GlyphProvider provider : providers) {
			IntSet glyphs = provider.getSupportedGlyphs();
			try {
				total = Math.addExact(total, glyphs.size());
			} catch (ArithmeticException ignored) {
				return -1L;
			}
		}
		return total;
	}

	private static ArrayList<GlyphProvider> reverse(ArrayList<GlyphProvider> providers) {
		java.util.Collections.reverse(providers);
		return providers;
	}

	private static final class Bundle implements AutoCloseable {
		private final PreparationBudget.Scope scope;
		private Map<StackKey, RawFontPreparedSelection> selections;
		private boolean closed;

		private Bundle(PreparationBudget.Scope scope, Map<StackKey, RawFontPreparedSelection> selections) {
			this.scope = scope;
			this.selections = new LinkedHashMap<>(selections);
		}

		@Override public synchronized void close() {
			if (closed) return;
			closed = true;
			selections.values().forEach(RawFontPreparedSelection::close);
			selections = Map.of();
		}
	}

	private static final class StackKey {
		private final List<IdentityRef> providers;
		private final int hash;

		private StackKey(List<IdentityRef> providers) {
			this.providers = List.copyOf(providers);
			this.hash = this.providers.hashCode();
		}

		static StackKey of(List<GlyphProvider> providers) {
			ArrayList<IdentityRef> refs = new ArrayList<>(providers.size());
			for (GlyphProvider provider : providers) refs.add(new IdentityRef(provider));
			return new StackKey(refs);
		}

		@Override public boolean equals(Object other) {
			return this == other || other instanceof StackKey key && providers.equals(key.providers);
		}
		@Override public int hashCode() { return hash; }
	}

	private static final class IdentityRef {
		private final Object value;
		private final int hash;
		private IdentityRef(Object value) { this.value = value; this.hash = System.identityHashCode(value); }
		@Override public boolean equals(Object other) { return other instanceof IdentityRef ref && value == ref.value; }
		@Override public int hashCode() { return hash; }
	}

	private RawFontSelectionRegistry() {}
}
