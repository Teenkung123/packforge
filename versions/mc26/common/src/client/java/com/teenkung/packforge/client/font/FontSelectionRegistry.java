package com.teenkung.packforge.client.font;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.concurrent.OrderedAsync;
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
	private static final Map<Object, FontPreparationBundle> PREPARED = new IdentityHashMap<>();

	public static boolean preparationHooksEnabled() {
		ReloadFeatureSnapshot features = reloadFeatures();
		return features == null
			? FeatureFlags.fontPrepareProviderSelectionEnabled() || FeatureFlags.fontReloadDiagnosticsEnabled()
			: features.fontPrepareProviderSelectionEnabled() || features.fontReloadDiagnosticsEnabled();
	}

	public static Object prepare(Object preparation, Set<FontOption> options) {
		if (!preparationHooksEnabled()) {
			return preparation;
		}
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets = fontSets(preparation);
		ReloadFeatureSnapshot features = reloadFeatures();
		boolean selectionEnabled = features == null
			? FeatureFlags.fontPrepareProviderSelectionEnabled()
			: features.fontPrepareProviderSelectionEnabled();
		boolean diagnosticsEnabled = features == null
			? FeatureFlags.fontReloadDiagnosticsEnabled()
			: features.fontReloadDiagnosticsEnabled();
		List<StackGroup> groups = groupFontSets(fontSets);
		List<FontPreparedSelection> selections = new ArrayList<>(groups.size());
		for (StackGroup group : groups) {
			selections.add(selectionEnabled ? FontPreparedSelection.compute(group.providers(), options) : null);
		}
		return store(preparation, options, fontSets, groups, selections, diagnosticsEnabled, selectionEnabled);
	}

	public static CompletableFuture<Object> prepareAsync(
		Object preparation,
		Set<FontOption> options,
		Executor executor
	) {
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
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets = fontSets(preparation);
		List<StackGroup> groups = groupFontSets(fontSets);
		int workerBudget = features == null ? fallbackWorkerBudget() : features.workerBudget();
		ReloadExecutionContext context = ReloadExecutionContext.current();
		CompletableFuture<List<FontPreparedSelection>> selectionsFuture = selectionEnabled
			? OrderedAsync.map(
				groups,
				executor,
				workerBudget,
				1,
				group -> FontPreparedSelection.compute(group.providers(), options),
				selection -> { }
			)
			: CompletableFuture.completedFuture(List.of());
		CompletableFuture<Object> result = selectionsFuture.thenApply(selections -> {
			if (context != null && !ReloadExecutionContext.isCurrent(context)) {
				return preparation;
			}
			return store(preparation, options, fontSets, groups, selections, diagnosticsEnabled, selectionEnabled);
		});
		result.whenComplete((ignored, error) -> {
			if (error != null) {
				discard(preparation);
			}
		});
		return result;
	}

	public static void beginApply(Object preparation) {
		synchronized (PREPARED) {
			APPLYING.set(PREPARED.remove(preparation));
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
		APPLYING.remove();
		CURRENT_FONT_ID.remove();
	}

	public static void resetForReload() {
		clear();
		synchronized (PREPARED) {
			PREPARED.clear();
		}
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

	private static Object store(
		Object preparation,
		Set<FontOption> options,
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets,
		List<StackGroup> groups,
		List<FontPreparedSelection> selections,
		boolean diagnosticsEnabled,
		boolean selectionEnabled
	) {
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
		FontPreparationBundle bundle = new FontPreparationBundle(options, byId, byStack, diagnostics);
		synchronized (PREPARED) {
			PREPARED.put(preparation, bundle);
		}
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
		synchronized (PREPARED) {
			PREPARED.remove(preparation);
		}
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
