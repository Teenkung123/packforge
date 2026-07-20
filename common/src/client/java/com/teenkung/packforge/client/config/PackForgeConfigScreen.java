package com.teenkung.packforge.client.config;

import com.teenkung.packforge.client.compat.MinecraftGuiCompat;
import com.teenkung.packforge.config.PackForgeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PackForgeConfigScreen extends Screen {
	private static final String CATEGORY_RELOAD = "Reload Optimizer";
	private static final String CATEGORY_ATLAS = "Large Atlas Fixer";
	private static final String CATEGORY_STARTUP = "Startup Optimizer";
	private final Screen parent;
	private final PackForgeConfig.Cfg cfg;
	private final PackForgeConfig.Cfg defaults = new PackForgeConfig.Cfg();
	private final List<RowSpec> rows = new ArrayList<>();
	private EditBox searchBox;
	private ConfigList list;
	private String filter = "";
	private String activeCategory = CATEGORY_RELOAD;

	public PackForgeConfigScreen(Screen parent) {
		super(Component.literal("PackForge Config"));
		this.parent = parent;
		this.cfg = PackForgeConfig.get();
		defineRows();
	}

	@Override
	protected void init() {
		searchBox = new EditBox(this.font, this.width / 2 - 150, 24, 300, 20, Component.literal("Search"));
		searchBox.setHint(Component.literal("Search..."));
		searchBox.setValue(filter);
		searchBox.setResponder(value -> {
			filter = value.toLowerCase(Locale.ROOT).trim();
			rebuildList();
		});
		addRenderableWidget(searchBox);

		addRenderableWidget(categoryButton(CATEGORY_RELOAD, this.width / 2 - 231, 50));
		addRenderableWidget(categoryButton(CATEGORY_ATLAS, this.width / 2 - 75, 50));
		addRenderableWidget(categoryButton(CATEGORY_STARTUP, this.width / 2 + 81, 50));

		list = new ConfigList(this.minecraft, this.width, this.height - 118, 78, 26);
		addRenderableWidget(list);
		rebuildList();

		addRenderableWidget(Button.builder(Component.literal("Reset All"), button -> {
			copyDefaults();
			PackForgeConfig.save();
			rebuildList();
		}).bounds(this.width / 2 - 154, this.height - 32, 100, 20).tooltip(Tooltip.create(Component.literal("Restore PackForge defaults."))).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
			PackForgeConfig.save();
			MinecraftGuiCompat.setScreen(this.minecraft, parent);
		}).bounds(this.width / 2 + 54, this.height - 32, 100, 20).build());
		addRenderableOnly(new StringWidget(0, 8, this.width, 12, this.title, this.font));
	}

	@Override
	public void onClose() {
		PackForgeConfig.save();
		MinecraftGuiCompat.setScreen(this.minecraft, parent);
	}

	private void rebuildList() {
		if (list == null) {
			return;
		}
		list.clear();
		String currentSection = "";
		for (RowSpec row : rows) {
			if (!row.category.equals(activeCategory)) {
				continue;
			}
			if (!row.matches(filter)) {
				continue;
			}
			if (!row.section.equals(currentSection)) {
				currentSection = row.section;
				list.add(new SectionEntry(currentSection, this.font));
			}
			list.add(new OptionEntry(row, this.font));
		}
	}

	private void defineRows() {
		booleanRow(CATEGORY_RELOAD, "General", "Enable reload optimizer", "Master switch for PackForge reload-speed optimizations. Child settings stay saved while this is off.",
			() -> cfg.reloadOptimizerEnabled, value -> cfg.reloadOptimizerEnabled = value, () -> defaults.reloadOptimizerEnabled);
		booleanRow(CATEGORY_RELOAD, "Pack index", "Index packs", "Build per-pack resource indexes. Main optimization for getResource/listResources namespace lookups.",
			() -> cfg.loaderIndexEnabled, value -> cfg.loaderIndexEnabled = value, () -> defaults.loaderIndexEnabled);
		booleanRow(CATEGORY_RELOAD, "Pack index", "ZIP read pool", "Experimental. Open per-thread ZIP handles for parallel resource reads. Default off until benchmarked.",
			() -> cfg.loaderZipPoolEnabled, value -> cfg.loaderZipPoolEnabled = value, () -> defaults.loaderZipPoolEnabled);
		booleanRow(CATEGORY_RELOAD, "Reload UI", "Loading status overlay", "Show current reload listener on Mojang loading overlay.",
			() -> cfg.loadingStatusOverlayEnabled, value -> cfg.loadingStatusOverlayEnabled = value, () -> defaults.loadingStatusOverlayEnabled);
		booleanRow(CATEGORY_RELOAD, "Reload UI", "Disable loading fade out", "Remove Mojang loading overlay immediately after reload completion.",
			() -> cfg.loadingScreenFadeOutDisabled, value -> cfg.loadingScreenFadeOutDisabled = value, () -> defaults.loadingScreenFadeOutDisabled);
		booleanRow(CATEGORY_RELOAD, "Reload UI", "Pack reload summary toast", "Show a toast with total pack reload time after reload completion.",
			() -> cfg.reloadSummaryToastEnabled, value -> cfg.reloadSummaryToastEnabled = value, () -> defaults.reloadSummaryToastEnabled);
		booleanRow(CATEGORY_RELOAD, "Fonts", "Prepare font provider selection", "Move CPU-only FontSet provider selection off render-thread apply. Big freeze reducer.",
			() -> cfg.fontPrepareProviderSelectionEnabled, value -> cfg.fontPrepareProviderSelectionEnabled = value, () -> defaults.fontPrepareProviderSelectionEnabled);
		booleanRow(CATEGORY_RELOAD, "Fonts", "Bitmap provider cache", "Experimental. Keep off until font resource fingerprinting is proven stable.",
			() -> cfg.fontBitmapProviderCacheEnabled, value -> cfg.fontBitmapProviderCacheEnabled = value, () -> defaults.fontBitmapProviderCacheEnabled);
		booleanRow(CATEGORY_RELOAD, "Models", "Model parse batching", "Batch block-model parse jobs to reduce executor overhead on large packs.",
			() -> cfg.modelParseBatchingEnabled, value -> cfg.modelParseBatchingEnabled = value, () -> defaults.modelParseBatchingEnabled);
		intRow(CATEGORY_RELOAD, "Models", "Model parse batch size", "Number of block model JSON files parsed per worker task.",
			() -> cfg.modelParseBatchSize, value -> cfg.modelParseBatchSize = clamp(value, 8, 1024), () -> defaults.modelParseBatchSize);
		booleanRow(CATEGORY_RELOAD, "Models", "Model parse timings", "Development use. Logs model list/read/parse/collect timings.",
			() -> cfg.modelParseTimingEnabled, value -> cfg.modelParseTimingEnabled = value, () -> defaults.modelParseTimingEnabled);
		booleanRow(CATEGORY_RELOAD, "Models", "Adaptive model batching", "Experimental. Adjust model parse batch size from resource count.",
			() -> cfg.modelAdaptiveBatchingEnabled, value -> cfg.modelAdaptiveBatchingEnabled = value, () -> defaults.modelAdaptiveBatchingEnabled);
		booleanRow(CATEGORY_RELOAD, "Models", "Duplicate model parse cache", "Experimental. Reload-scoped cache for exact duplicate immutable model JSON parses.",
			() -> cfg.modelDuplicateParseCacheEnabled, value -> cfg.modelDuplicateParseCacheEnabled = value, () -> defaults.modelDuplicateParseCacheEnabled);
		booleanRow(CATEGORY_RELOAD, "Logging", "Loader timings", "Debug log for PackForge resource index counters. Development use.",
			() -> cfg.loaderTimingsEnabled, value -> cfg.loaderTimingsEnabled = value, () -> defaults.loaderTimingsEnabled);
		booleanRow(CATEGORY_RELOAD, "Logging", "Reload listener timings", "Debug loading summary and CSV listener timings. Development use.",
			() -> cfg.reloadListenerTimingsEnabled, value -> cfg.reloadListenerTimingsEnabled = value, () -> defaults.reloadListenerTimingsEnabled);
		booleanRow(CATEGORY_RELOAD, "Logging", "Shader apply stall diagnostics", "Label long Shader Loader render-thread stalls after reloads.",
			() -> cfg.shaderApplyStallDiagnosticsEnabled, value -> cfg.shaderApplyStallDiagnosticsEnabled = value, () -> defaults.shaderApplyStallDiagnosticsEnabled);
		booleanRow(CATEGORY_RELOAD, "Compatibility", "ImmediatelyFast font atlas guard", "Avoid re-enabling ImmediatelyFast font atlas resizing during pack removal reloads.",
			() -> cfg.immediatelyFastFontAtlasCompatEnabled, value -> cfg.immediatelyFastFontAtlasCompatEnabled = value, () -> defaults.immediatelyFastFontAtlasCompatEnabled);
		booleanRow(CATEGORY_RELOAD, "Logging", "Font reload diagnostics", "Debug font provider counts and CSV font timings. Development use.",
			() -> cfg.fontReloadDiagnosticsEnabled, value -> cfg.fontReloadDiagnosticsEnabled = value, () -> defaults.fontReloadDiagnosticsEnabled);
		booleanRow(CATEGORY_RELOAD, "Logging", "Atlas phase timings", "Development use. Logs source, decode, stitch, mip, and upload atlas timings.",
			() -> cfg.atlasPhaseTimingsEnabled, value -> cfg.atlasPhaseTimingsEnabled = value, () -> defaults.atlasPhaseTimingsEnabled);
		booleanRow(CATEGORY_RELOAD, "Atlas decode", "Atlas decode batching", "Experimental. Batch atlas sprite decode jobs to reduce future overhead.",
			() -> cfg.atlasDecodeBatchingEnabled, value -> cfg.atlasDecodeBatchingEnabled = value, () -> defaults.atlasDecodeBatchingEnabled);
		intRow(CATEGORY_RELOAD, "Atlas decode", "Atlas decode batch size", "Sprite source loaders processed per decode worker task.",
			() -> cfg.atlasDecodeBatchSize, value -> cfg.atlasDecodeBatchSize = clamp(value, 16, 4096), () -> defaults.atlasDecodeBatchSize);

		booleanRow(CATEGORY_ATLAS, "General", "Enable large atlas fixer", "Master switch for atlas overflow and atlas-related resource-pack compatibility fixes. Child settings stay saved while this is off.",
			() -> cfg.largeAtlasFixerEnabled, value -> cfg.largeAtlasFixerEnabled = value, () -> defaults.largeAtlasFixerEnabled);
		booleanRow(CATEGORY_ATLAS, "Compatibility", "Clamp model UV transparency probe", "Avoid crashes from resource packs with tiny negative UVs during translucency checks.",
			() -> cfg.modelUvTransparencyClampEnabled, value -> cfg.modelUvTransparencyClampEnabled = value, () -> defaults.modelUvTransparencyClampEnabled);
		booleanRow(CATEGORY_ATLAS, "Atlas cap", "Atlas cap", "Cap oversized sprite frames before atlas stitching to prevent atlas overflow.",
			() -> cfg.atlasCapEnabled, value -> cfg.atlasCapEnabled = value, () -> defaults.atlasCapEnabled);
		intRow(CATEGORY_ATLAS, "Atlas cap", "Atlas cap pixels", "Maximum sprite frame size when atlas cap is enabled.",
			() -> cfg.atlasCapPx, value -> cfg.atlasCapPx = clamp(value, 16, 8192), () -> defaults.atlasCapPx);
		textRow(CATEGORY_ATLAS, "Atlas cap", "Atlas exclude ids", "Comma-separated atlas ids excluded from sprite capping.",
			() -> String.join(", ", cfg.atlasExcludeIds), () -> String.join(", ", defaults.atlasExcludeIds));
		booleanRow(CATEGORY_ATLAS, "Atlas retry", "Atlas retry", "Experimental retry path for atlas overflow recovery.",
			() -> cfg.atlasRetryEnabled, value -> cfg.atlasRetryEnabled = value, () -> defaults.atlasRetryEnabled);
		intRow(CATEGORY_ATLAS, "Atlas retry", "Atlas retry attempts", "Maximum atlas retry attempts.",
			() -> cfg.atlasRetryMaxAttempts, value -> cfg.atlasRetryMaxAttempts = clamp(value, 1, 10), () -> defaults.atlasRetryMaxAttempts);
		booleanRow(CATEGORY_ATLAS, "Atlas retry", "Disable retry with Iris", "Force atlas retry off when Iris is installed, unless you want to test risky behavior.",
			() -> cfg.forceDisablePartIIIWithIris, value -> cfg.forceDisablePartIIIWithIris = value, () -> defaults.forceDisablePartIIIWithIris);
		booleanRow(CATEGORY_ATLAS, "Atlas mipmaps", "Parallel mip generation", "Experimental. Split atlas mipmap generation into bounded worker batches.",
			() -> cfg.atlasMipParallelEnabled, value -> cfg.atlasMipParallelEnabled = value, () -> defaults.atlasMipParallelEnabled);
		intRow(CATEGORY_ATLAS, "Atlas mipmaps", "Mipmap batch size", "Sprites processed per mipmap worker batch.",
			() -> cfg.atlasMipBatchSize, value -> cfg.atlasMipBatchSize = clamp(value, 16, 4096), () -> defaults.atlasMipBatchSize);
		booleanRow(CATEGORY_ATLAS, "Experimental split", "Atlas split gate", "Reserved guard for future item/particle split experiments. Blocks are always rejected.",
			() -> cfg.experimentalAtlasSplit, value -> cfg.experimentalAtlasSplit = value, () -> defaults.experimentalAtlasSplit);
		textListRow(CATEGORY_ATLAS, "Experimental split", "Atlas split targets", "Comma-separated safe targets. Only minecraft:items and minecraft:particles are accepted.",
			() -> String.join(", ", cfg.atlasSplitTargets), value -> cfg.atlasSplitTargets = parseSafeSplitTargets(value), () -> String.join(", ", defaults.atlasSplitTargets));
		intRow(CATEGORY_ATLAS, "Experimental split", "Atlas split max tiers", "Reserved tier count for future item/particle overflow atlases.",
			() -> cfg.atlasSplitMaxTiers, value -> cfg.atlasSplitMaxTiers = clamp(value, 1, 4), () -> defaults.atlasSplitMaxTiers);
		booleanRow(CATEGORY_ATLAS, "Experimental split", "Split fallback downscale", "Keep downscale/cap fallback active for any unsafe split case.",
			() -> cfg.atlasSplitFallbackToDownscale, value -> cfg.atlasSplitFallbackToDownscale = value, () -> defaults.atlasSplitFallbackToDownscale);
		booleanRow(CATEGORY_ATLAS, "Experimental split", "Disable split with Iris", "Compatibility guard for shader stacks. Default on.",
			() -> cfg.atlasSplitDisableWithIris, value -> cfg.atlasSplitDisableWithIris = value, () -> defaults.atlasSplitDisableWithIris);
		booleanRow(CATEGORY_ATLAS, "Experimental split", "Disable split with Sodium", "Extra compatibility guard. Default off so item experiments can be tested.",
			() -> cfg.atlasSplitDisableWithSodium, value -> cfg.atlasSplitDisableWithSodium = value, () -> defaults.atlasSplitDisableWithSodium);
		booleanRow(CATEGORY_ATLAS, "Experimental split", "Split model coherence", "Reserved guard: route all textures for one item model together when split exists.",
			() -> cfg.atlasSplitModelCoherence, value -> cfg.atlasSplitModelCoherence = value, () -> defaults.atlasSplitModelCoherence);
		booleanRow(CATEGORY_ATLAS, "Experimental split", "Split diagnostics", "Reserved logging/reporting switch for future split experiments.",
			() -> cfg.atlasSplitDiagnostics, value -> cfg.atlasSplitDiagnostics = value, () -> defaults.atlasSplitDiagnostics);

		booleanRow(CATEGORY_STARTUP, "General", "Enable startup optimizer", "Master switch for experimental startup/init optimizations. Default off.",
			() -> cfg.startupOptimizerEnabled, value -> cfg.startupOptimizerEnabled = value, () -> defaults.startupOptimizerEnabled);
		booleanRow(CATEGORY_STARTUP, "General", "Startup timings", "Log startup optimizer timing events and CSV rows when startup optimizer is on.",
			() -> cfg.startupTimingsEnabled, value -> cfg.startupTimingsEnabled = value, () -> defaults.startupTimingsEnabled);
		booleanRow(CATEGORY_STARTUP, "General", "Startup status overlay", "Show current startup optimizer phase on the loading screen.",
			() -> cfg.startupStatusOverlayEnabled, value -> cfg.startupStatusOverlayEnabled = value, () -> defaults.startupStatusOverlayEnabled);
		booleanRow(CATEGORY_STARTUP, "Executors", "Executor tuning", "Tune Minecraft background worker thread count and priority during executor creation.",
			() -> cfg.startupExecutorTuningEnabled, value -> cfg.startupExecutorTuningEnabled = value, () -> defaults.startupExecutorTuningEnabled);
		intRow(CATEGORY_STARTUP, "Executors", "Startup worker threads", "Background worker threads. Use 0 for auto.",
			() -> cfg.startupWorkerThreads, value -> cfg.startupWorkerThreads = clamp(value, 0, Runtime.getRuntime().availableProcessors()), () -> defaults.startupWorkerThreads);
		intRow(CATEGORY_STARTUP, "Executors", "Startup thread priority", "Priority for tuned Minecraft startup worker threads. Valid range is 1 to 10.",
			() -> cfg.startupThreadPriority, value -> cfg.startupThreadPriority = clamp(value, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY), () -> defaults.startupThreadPriority);
		booleanRow(CATEGORY_STARTUP, "Compatibility", "Skip with Smooth Boot", "Skip executor tuning when a Smooth Boot variant is installed.",
			() -> cfg.startupSkipWithSmoothBoot, value -> cfg.startupSkipWithSmoothBoot = value, () -> defaults.startupSkipWithSmoothBoot);
		booleanRow(CATEGORY_STARTUP, "Future async work", "Async data parsing", "Use reload-safe async model JSON batching and duplicate parse cache.",
			() -> cfg.startupAsyncDataParsingEnabled, value -> cfg.startupAsyncDataParsingEnabled = value, () -> defaults.startupAsyncDataParsingEnabled);
		booleanRow(CATEGORY_STARTUP, "Future async work", "Async class scanning", "Background mod-jar scan telemetry only. Does not change loader entrypoint order.",
			() -> cfg.startupAsyncClassScanEnabled, value -> cfg.startupAsyncClassScanEnabled = value, () -> defaults.startupAsyncClassScanEnabled);
		booleanRow(CATEGORY_STARTUP, "Future async work", "Async font/atlas startup", "Use CPU-only async font selection, atlas decode batching, and mip prep.",
			() -> cfg.startupAsyncFontAtlasEnabled, value -> cfg.startupAsyncFontAtlasEnabled = value, () -> defaults.startupAsyncFontAtlasEnabled);
	}

	private Button categoryButton(String category, int x, int y) {
		Button button = Button.builder(categoryText(category), widget -> {
			activeCategory = category;
			rebuildWidgets();
		}).bounds(x, y, 150, 20).build();
		button.setTooltip(Tooltip.create(Component.literal(category.equals(CATEGORY_RELOAD)
			? "Options that reduce resource-pack reload time and show reload diagnostics."
			: category.equals(CATEGORY_ATLAS)
				? "Options that prevent oversized atlas crashes and related pack compatibility failures."
				: "Experimental options for startup/init timing and worker executor tuning.")));
		return button;
	}

	private Component categoryText(String category) {
		return Component.literal(category).withStyle(category.equals(activeCategory) ? ChatFormatting.GREEN : ChatFormatting.WHITE);
	}

	private void booleanRow(String category, String section, String name, String description, BooleanSupplier getter, BoolSetter setter, BooleanSupplier defaultGetter) {
		rows.add(new RowSpec(category, section, name, description, () -> {
			Button button = Button.builder(booleanText(getter.getAsBoolean()), widget -> {
				boolean next = !getter.getAsBoolean();
				setter.accept(next);
				widget.setMessage(booleanText(next));
				PackForgeConfig.save();
			}).size(120, 20).build();
			button.setTooltip(Tooltip.create(Component.literal(description)));
			return button;
		}, () -> {
			setter.accept(defaultGetter.getAsBoolean());
			PackForgeConfig.save();
		}));
	}

	private static Component booleanText(boolean value) {
		return Component.literal(value ? "ON" : "OFF").withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private void intRow(String category, String section, String name, String description, IntSupplier getter, IntConsumer setter, IntSupplier defaultGetter) {
		rows.add(new RowSpec(category, section, name, description, () -> {
			EditBox box = new EditBox(this.font, 0, 0, 120, 20, Component.literal(name));
			box.setValue(Integer.toString(getter.getAsInt()));
			box.setTooltip(Tooltip.create(Component.literal(description)));
			box.setResponder(value -> parseInt(value, setter));
			return box;
		}, () -> {
			setter.accept(defaultGetter.getAsInt());
			PackForgeConfig.save();
		}));
	}

	private void textRow(String category, String section, String name, String description, Supplier<String> getter, Supplier<String> defaultGetter) {
		textListRow(category, section, name, description, getter, value -> cfg.atlasExcludeIds = parseList(value), defaultGetter);
	}

	private void textListRow(String category, String section, String name, String description, Supplier<String> getter, Consumer<String> setter, Supplier<String> defaultGetter) {
		rows.add(new RowSpec(category, section, name, description, () -> {
			EditBox box = new EditBox(this.font, 0, 0, 180, 20, Component.literal(name));
			box.setMaxLength(512);
			box.setValue(getter.get());
			box.setTooltip(Tooltip.create(Component.literal(description)));
			box.setResponder(value -> {
				setter.accept(value);
				PackForgeConfig.save();
			});
			return box;
		}, () -> {
			setter.accept(defaultGetter.get());
			PackForgeConfig.save();
		}));
	}

	private void parseInt(String value, IntConsumer setter) {
		try {
			setter.accept(Integer.parseInt(value.trim()));
			PackForgeConfig.save();
		} catch (NumberFormatException ignored) {
		}
	}

	private void copyDefaults() {
		cfg.reloadOptimizerEnabled = defaults.reloadOptimizerEnabled;
		cfg.largeAtlasFixerEnabled = defaults.largeAtlasFixerEnabled;
		cfg.loaderIndexEnabled = defaults.loaderIndexEnabled;
		cfg.loaderZipPoolEnabled = defaults.loaderZipPoolEnabled;
		cfg.loaderTimingsEnabled = defaults.loaderTimingsEnabled;
		cfg.reloadListenerTimingsEnabled = defaults.reloadListenerTimingsEnabled;
		cfg.shaderApplyStallDiagnosticsEnabled = defaults.shaderApplyStallDiagnosticsEnabled;
		cfg.immediatelyFastFontAtlasCompatEnabled = defaults.immediatelyFastFontAtlasCompatEnabled;
		cfg.loadingStatusOverlayEnabled = defaults.loadingStatusOverlayEnabled;
		cfg.loadingScreenFadeOutDisabled = defaults.loadingScreenFadeOutDisabled;
		cfg.reloadSummaryToastEnabled = defaults.reloadSummaryToastEnabled;
		cfg.modelUvTransparencyClampEnabled = defaults.modelUvTransparencyClampEnabled;
		cfg.fontReloadDiagnosticsEnabled = defaults.fontReloadDiagnosticsEnabled;
		cfg.fontPrepareProviderSelectionEnabled = defaults.fontPrepareProviderSelectionEnabled;
		cfg.fontBitmapProviderCacheEnabled = defaults.fontBitmapProviderCacheEnabled;
		cfg.atlasPhaseTimingsEnabled = defaults.atlasPhaseTimingsEnabled;
		cfg.atlasMipParallelEnabled = defaults.atlasMipParallelEnabled;
		cfg.atlasMipBatchSize = defaults.atlasMipBatchSize;
		cfg.atlasDecodeBatchingEnabled = defaults.atlasDecodeBatchingEnabled;
		cfg.atlasDecodeBatchSize = defaults.atlasDecodeBatchSize;
		cfg.modelParseBatchingEnabled = defaults.modelParseBatchingEnabled;
		cfg.modelParseBatchSize = defaults.modelParseBatchSize;
		cfg.modelParseTimingEnabled = defaults.modelParseTimingEnabled;
		cfg.modelAdaptiveBatchingEnabled = defaults.modelAdaptiveBatchingEnabled;
		cfg.modelDuplicateParseCacheEnabled = defaults.modelDuplicateParseCacheEnabled;
		cfg.atlasCapEnabled = defaults.atlasCapEnabled;
		cfg.atlasCapPx = defaults.atlasCapPx;
		cfg.atlasExcludeIds = new ArrayList<>(defaults.atlasExcludeIds);
		cfg.atlasRetryEnabled = defaults.atlasRetryEnabled;
		cfg.atlasRetryMaxAttempts = defaults.atlasRetryMaxAttempts;
		cfg.forceDisablePartIIIWithIris = defaults.forceDisablePartIIIWithIris;
		cfg.experimentalAtlasSplit = defaults.experimentalAtlasSplit;
		cfg.atlasSplitTargets = new ArrayList<>(defaults.atlasSplitTargets);
		cfg.atlasSplitMaxTiers = defaults.atlasSplitMaxTiers;
		cfg.atlasSplitFallbackToDownscale = defaults.atlasSplitFallbackToDownscale;
		cfg.atlasSplitDisableWithIris = defaults.atlasSplitDisableWithIris;
		cfg.atlasSplitDisableWithSodium = defaults.atlasSplitDisableWithSodium;
		cfg.atlasSplitModelCoherence = defaults.atlasSplitModelCoherence;
		cfg.atlasSplitDiagnostics = defaults.atlasSplitDiagnostics;
		cfg.startupOptimizerEnabled = defaults.startupOptimizerEnabled;
		cfg.startupTimingsEnabled = defaults.startupTimingsEnabled;
		cfg.startupStatusOverlayEnabled = defaults.startupStatusOverlayEnabled;
		cfg.startupExecutorTuningEnabled = defaults.startupExecutorTuningEnabled;
		cfg.startupWorkerThreads = defaults.startupWorkerThreads;
		cfg.startupThreadPriority = defaults.startupThreadPriority;
		cfg.startupSkipWithSmoothBoot = defaults.startupSkipWithSmoothBoot;
		cfg.startupAsyncDataParsingEnabled = defaults.startupAsyncDataParsingEnabled;
		cfg.startupAsyncClassScanEnabled = defaults.startupAsyncClassScanEnabled;
		cfg.startupAsyncFontAtlasEnabled = defaults.startupAsyncFontAtlasEnabled;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static List<String> parseList(String value) {
		List<String> result = new ArrayList<>();
		for (String part : value.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}
		return result;
	}

	private static List<String> parseSafeSplitTargets(String value) {
		List<String> result = new ArrayList<>();
		for (String part : value.split(",")) {
			String trimmed = part.trim().toLowerCase(Locale.ROOT);
			if ((trimmed.equals("minecraft:items") || trimmed.equals("minecraft:particles")) && !result.contains(trimmed)) {
				result.add(trimmed);
			}
		}
		return result;
	}

	@FunctionalInterface
	private interface BoolSetter {
		void accept(boolean value);
	}

	private record RowSpec(String category, String section, String name, String description, Supplier<AbstractWidget> controlFactory, Runnable reset) {
		boolean matches(String filter) {
			return filter.isEmpty()
				|| section.toLowerCase(Locale.ROOT).contains(filter)
				|| name.toLowerCase(Locale.ROOT).contains(filter)
				|| description.toLowerCase(Locale.ROOT).contains(filter);
		}
	}

	private static final class ConfigList extends ContainerObjectSelectionList<ConfigEntry> {
		ConfigList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			this.centerListVertically = false;
		}

		void add(ConfigEntry entry) {
			addEntry(entry);
		}

		void clear() {
			clearEntries();
		}

		@Override
		public int getRowWidth() {
			return Math.min(760, this.width - 80);
		}
	}

	private abstract static class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {
	}

	private static final class SectionEntry extends ConfigEntry {
		private final StringWidget label;
		private final int textWidth;

		SectionEntry(String name, net.minecraft.client.gui.Font font) {
			this.label = new StringWidget(0, 0, 260, 20, Component.literal(name), font);
			this.textWidth = font.width(name);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickProgress) {
			place();
			label.extractRenderState(graphics, mouseX, mouseY, tickProgress);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			place();
			return List.of(label);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(label);
		}

		@Override
		public void visitWidgets(java.util.function.Consumer<AbstractWidget> consumer) {
			place();
			consumer.accept(label);
		}

		private void place() {
			label.setX(getContentXMiddle() - textWidth / 2);
			label.setY(getContentY() + 5);
		}
	}

	private static final class OptionEntry extends ConfigEntry {
		private final RowSpec spec;
		private final StringWidget label;
		private final AbstractWidget control;
		private final Button reset;

		OptionEntry(RowSpec spec, net.minecraft.client.gui.Font font) {
			this.spec = spec;
			this.label = new StringWidget(0, 0, 260, 20, Component.literal(spec.name()), font);
			this.label.setTooltip(Tooltip.create(Component.literal(spec.description())));
			this.control = spec.controlFactory().get();
			this.reset = Button.builder(Component.literal("Reset"), button -> spec.reset().run())
				.size(56, 20)
				.tooltip(Tooltip.create(Component.literal("Restore default for " + spec.name() + ".")))
				.build();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickProgress) {
			place();
			label.extractRenderState(graphics, mouseX, mouseY, tickProgress);
			control.extractRenderState(graphics, mouseX, mouseY, tickProgress);
			reset.extractRenderState(graphics, mouseX, mouseY, tickProgress);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			place();
			return List.of(label, control, reset);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(label, control, reset);
		}

		@Override
		public void visitWidgets(java.util.function.Consumer<AbstractWidget> consumer) {
			place();
			consumer.accept(label);
			consumer.accept(control);
			consumer.accept(reset);
		}

		private void place() {
			int x = getContentX();
			int y = getContentY() + 3;
			int right = getContentRight();
			label.setX(x);
			label.setY(y + 5);
			control.setX(right - control.getWidth() - 64);
			control.setY(y);
			reset.setX(right - 56);
			reset.setY(y);
		}
	}
}
