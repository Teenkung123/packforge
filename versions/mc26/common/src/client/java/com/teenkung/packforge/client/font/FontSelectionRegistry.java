package com.teenkung.packforge.client.font;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.concurrent.OrderedAsync;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Reload-scoped, unique-stack font selection registry. */
public final class FontSelectionRegistry {
	private static final ThreadLocal<FontPreparationBundle> APPLYING = new ThreadLocal<>();
	private static final ThreadLocal<Identifier> CURRENT_FONT_ID = new ThreadLocal<>();
	private static final Object PREPARED_LOCK = new Object();
	private static Map<Object, FontPreparationBundle> PREPARED = new IdentityHashMap<>();

	public static boolean preparationHooksEnabled() {
		ReloadFeatureSnapshot features = reloadFeatures();
		return features == null
			? FeatureFlags.fontPrepareProviderSelectionEnabled() || FeatureFlags.fontReloadDiagnosticsEnabled()
			: features.fontPrepareProviderSelectionEnabled() || features.fontReloadDiagnosticsEnabled();
	}

	public static Object prepare(Object preparation, Set<FontOption> options) {
		return prepareAsync(preparation, options, Runnable::run).join();
	}

	public static CompletableFuture<Object> prepareAsync(
		Object preparation,
		Set<FontOption> options,
		Executor executor
	) {
		return prepareAsync(preparation, options, executor, null);
	}

	public static CompletableFuture<Object> prepareAsync(Object preparation, Set<FontOption> options,
		Executor executor, FontPreparationCoordinator coordinator) {
		ReloadFeatureSnapshot features = reloadFeatures();
		boolean selectionEnabled = features == null
			? FeatureFlags.fontPrepareProviderSelectionEnabled()
			: features.fontPrepareProviderSelectionEnabled();
		boolean diagnosticsEnabled = features == null
			? FeatureFlags.fontReloadDiagnosticsEnabled()
			: features.fontReloadDiagnosticsEnabled();
		if (!selectionEnabled && !diagnosticsEnabled) {
			return CompletableFuture.completedFuture(preparation);
		}
		ReloadExecutionContext context = ReloadExecutionContext.current();
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets = fontSets(preparation);
		PreparationBudget.Reservation bookkeeping = reserveBookkeeping(context, fontSets);
		if (bookkeeping == null) {
			if (diagnosticsEnabled) PackForge.LOGGER.info("PackForge font preparation bypass: reason={} fonts={}",
				context == null ? "no reload budget" : "registry bookkeeping capacity", fontSets.size());
			if (coordinator != null) coordinator.close();
			return CompletableFuture.completedFuture(preparation);
		}
		FontPreparationCoordinator activeCoordinator;
		try {
			activeCoordinator = selectionEnabled && coordinator == null ? new FontPreparationCoordinator(context, options) : coordinator;
		} catch (RuntimeException | Error failure) {
			bookkeeping.close();
			throw failure;
		}
		try {
			List<StackGroup> groups = groupFontSets(fontSets);
			int workerBudget = features == null ? fallbackWorkerBudget() : features.workerBudget();
			CompletableFuture<Object> result = new CompletableFuture<>();
			CompletableFuture<List<FontPreparedSelection>> selectionsFuture = selectionEnabled
				? OrderedAsync.map(groups, executor, workerBudget, 1,
					group -> result.isCancelled() || !ReloadExecutionContext.isCurrent(context) ? null
						: activeCoordinator.selection(group.providers(), options), FontPreparedSelection::close)
				: CompletableFuture.completedFuture(List.of());
			selectionsFuture.whenComplete((selections, error) -> {
				boolean transferred = false;
				try {
					if (error != null) { result.completeExceptionally(error); return; }
					if (result.isCancelled() || !ReloadExecutionContext.isCurrent(context)) {
						closeSelections(selections);
						result.complete(preparation);
						return;
					}
					store(preparation, options, fontSets, groups, selections, diagnosticsEnabled, selectionEnabled,
						activeCoordinator, bookkeeping);
					transferred = true;
					if (!result.complete(preparation)) discard(preparation);
				} catch (RuntimeException | Error failure) {
					if (selections != null) closeSelections(selections);
					discard(preparation);
					result.completeExceptionally(failure);
				} finally {
					if (!transferred) {
						if (activeCoordinator != null) activeCoordinator.close();
						bookkeeping.close();
					}
				}
			});
			return result;
		} catch (RuntimeException | Error failure) {
			if (activeCoordinator != null) activeCoordinator.close();
			bookkeeping.close();
			throw failure;
		}
	}

	private static PreparationBudget.Reservation reserveBookkeeping(ReloadExecutionContext context,
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets) {
		if (context == null) return null;
		long slots = 0;
		for (List<GlyphProvider.Conditional> providers : fontSets.values()) slots += providers.size();
		// Group keys/copies, bounded executor tasks, result arrays, bundle indexes,
		// diagnostics and retirement callback all remain charged through ownership.
		return context.preparationBudget().tryReserve(16384L + fontSets.size() * 2048L + slots * 512L);
	}

	private static void closeSelections(List<FontPreparedSelection> selections) {
		for (FontPreparedSelection selection : selections) if (selection != null) selection.close();
	}

	public static void beginApply(Object preparation) {
		synchronized (PREPARED_LOCK) {
			APPLYING.set(PREPARED.remove(preparation));
			if (PREPARED.isEmpty()) PREPARED = new IdentityHashMap<>();
		}
	}

	public static FontPreparedSelection currentSelection(Identifier id, Set<FontOption> currentOptions) {
		FontPreparationBundle bundle = APPLYING.get();
		return bundle == null ? null : bundle.selectionFor(id, currentOptions);
	}

	public static FontPreparedSelection currentSelection(Set<FontOption> currentOptions) {
		FontPreparationBundle bundle = APPLYING.get();
		Identifier id = CURRENT_FONT_ID.get();
		return bundle == null || id == null ? null : bundle.selectionFor(id, currentOptions);
	}

	public static FontPreparedSelection currentSelection(
		List<GlyphProvider.Conditional> providers,
		Set<FontOption> currentOptions
	) {
		FontPreparationBundle bundle = APPLYING.get();
		return bundle == null ? null : bundle.selectionFor(providers, currentOptions);
	}

	public static FontPreparationBundle currentBundle() {
		return APPLYING.get();
	}

	public static void beginFontSet(Identifier id) {
		CURRENT_FONT_ID.set(id);
	}

	public static void endFontSet() {
		CURRENT_FONT_ID.remove();
	}

	public static void clear() {
		FontPreparationBundle bundle = APPLYING.get();
		APPLYING.remove();
		CURRENT_FONT_ID.remove();
		if (bundle != null) bundle.close();
	}

	public static void resetForReload() {
		clear();
		// Each stored preparation is removed by its own retirement callback.
		// A start hook from another reload must not discard newer preparations.
	}

	static List<StackGroup> groupFontSets(
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets
	) {
		Map<FontProviderStackKey, GroupBuilder> grouped = new LinkedHashMap<>();
		for (Map.Entry<Identifier, List<GlyphProvider.Conditional>> entry : fontSets.entrySet()) {
			List<GlyphProvider.Conditional> providers = List.copyOf(Lists.reverse(entry.getValue()));
			FontProviderStackKey key = FontProviderStackKey.of(providers);
			grouped.computeIfAbsent(key, ignored -> new GroupBuilder(key, providers)).ids.add(entry.getKey());
		}
		List<StackGroup> result = new ArrayList<>(grouped.size());
		for (GroupBuilder group : grouped.values()) {
			result.add(new StackGroup(group.key, group.providers, List.copyOf(group.ids)));
		}
		return List.copyOf(result);
	}

	static int fallbackWorkerBudget() {
		return Math.max(1, Math.min(32, Runtime.getRuntime().availableProcessors()));
	}

	private static Object store(Object preparation, Set<FontOption> options,
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets, List<StackGroup> groups,
		List<FontPreparedSelection> selections, boolean diagnosticsEnabled, boolean selectionEnabled,
		FontPreparationCoordinator coordinator, PreparationBudget.Reservation bookkeeping) {
		Map<Identifier, FontPreparedSelection> byId = new LinkedHashMap<>();
		Map<FontProviderStackKey, FontPreparedSelection> byStack = new LinkedHashMap<>();
		long selectionNs = 0L;
		for (int i = 0; i < groups.size(); i++) {
			FontPreparedSelection selection = selectionEnabled ? selections.get(i) : null;
			if (selection == null) {
				continue;
			}
			StackGroup group = groups.get(i);
			selectionNs += selection.elapsedNs();
			byStack.put(group.key(), selection);
			for (Identifier id : group.ids()) {
				byId.put(id, selection);
			}
		}
		FontReloadDiagnostics.Snapshot diagnostics = FontReloadDiagnostics.snapshot(
			fontSets,
			selectionNs,
			selectionEnabled ? Math.max(0, fontSets.size() - groups.size()) : 0,
			selectionEnabled ? groups.size() : 0,
			selectionEnabled ? groups.size() : 0,
			diagnosticsEnabled
		);
		FontPreparationBundle bundle = new FontPreparationBundle(options, byId, byStack, diagnostics, coordinator, bookkeeping);
		synchronized (PREPARED_LOCK) {
			PREPARED.put(preparation, bundle);
		}
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context != null) context.preparationBudget().onRetire(() -> discard(preparation));
		return preparation;
	}

	private static Map<Identifier, List<GlyphProvider.Conditional>> fontSets(Object preparation) {
		return ((FontManagerPreparationAccessor) preparation).packforge$fontSets();
	}

	private static ReloadFeatureSnapshot reloadFeatures() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null ? null : context.features();
	}

	private static void discard(Object preparation) {
		FontPreparationBundle bundle;
		synchronized (PREPARED_LOCK) {
			bundle = PREPARED.remove(preparation);
			if (PREPARED.isEmpty()) PREPARED = new IdentityHashMap<>();
		}
		if (bundle != null) bundle.close();
	}

	static record StackGroup(
		FontProviderStackKey key,
		List<GlyphProvider.Conditional> providers,
		List<Identifier> ids
	) {}

	private static final class GroupBuilder {
		private final FontProviderStackKey key;
		private final List<GlyphProvider.Conditional> providers;
		private final List<Identifier> ids = new ArrayList<>();

		private GroupBuilder(FontProviderStackKey key, List<GlyphProvider.Conditional> providers) {
			this.key = key;
			this.providers = providers;
		}
	}

	private FontSelectionRegistry() {}
}
