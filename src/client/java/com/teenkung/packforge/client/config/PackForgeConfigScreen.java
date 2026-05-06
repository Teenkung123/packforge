package com.teenkung.packforge.client.config;

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
import java.util.function.Supplier;

public final class PackForgeConfigScreen extends Screen {
	private final Screen parent;
	private final PackForgeConfig.Cfg cfg;
	private final PackForgeConfig.Cfg defaults = new PackForgeConfig.Cfg();
	private final List<RowSpec> rows = new ArrayList<>();
	private EditBox searchBox;
	private ConfigList list;
	private String filter = "";

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

		list = new ConfigList(this.minecraft, this.width, this.height - 92, 52, 26);
		addRenderableWidget(list);
		rebuildList();

		addRenderableWidget(Button.builder(Component.literal("Reset All"), button -> {
			copyDefaults();
			PackForgeConfig.save();
			rebuildList();
		}).bounds(this.width / 2 - 154, this.height - 32, 100, 20).tooltip(Tooltip.create(Component.literal("Restore PackForge defaults."))).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
			PackForgeConfig.save();
			this.minecraft.setScreen(parent);
		}).bounds(this.width / 2 + 54, this.height - 32, 100, 20).build());
		addRenderableOnly(new StringWidget(0, 8, this.width, 12, this.title, this.font));
	}

	@Override
	public void onClose() {
		PackForgeConfig.save();
		this.minecraft.setScreen(parent);
	}

	private void rebuildList() {
		if (list == null) {
			return;
		}
		list.clear();
		String currentSection = "";
		for (RowSpec row : rows) {
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
		booleanRow("Loader", "Index packs", "Build per-pack resource indexes. Main optimization for getResource/listResources namespace lookups.",
			() -> cfg.loaderIndexEnabled, value -> cfg.loaderIndexEnabled = value, () -> defaults.loaderIndexEnabled);
		booleanRow("Loader", "ZIP read pool", "Experimental. Open per-thread ZIP handles for parallel resource reads. Default off until benchmarked.",
			() -> cfg.loaderZipPoolEnabled, value -> cfg.loaderZipPoolEnabled = value, () -> defaults.loaderZipPoolEnabled);
		booleanRow("Loader", "Loading status overlay", "Show current reload listener on Mojang loading overlay.",
			() -> cfg.loadingStatusOverlayEnabled, value -> cfg.loadingStatusOverlayEnabled = value, () -> defaults.loadingStatusOverlayEnabled);
		booleanRow("Compatibility", "Clamp model UV transparency probe", "Avoid crashes from resource packs with tiny negative UVs during translucency checks.",
			() -> cfg.modelUvTransparencyClampEnabled, value -> cfg.modelUvTransparencyClampEnabled = value, () -> defaults.modelUvTransparencyClampEnabled);
		booleanRow("Fonts", "Prepare font provider selection", "Move CPU-only FontSet provider selection off render-thread apply. Big freeze reducer.",
			() -> cfg.fontPrepareProviderSelectionEnabled, value -> cfg.fontPrepareProviderSelectionEnabled = value, () -> defaults.fontPrepareProviderSelectionEnabled);
		booleanRow("Fonts", "Bitmap provider cache", "Experimental. Keep off until font resource fingerprinting is proven stable.",
			() -> cfg.fontBitmapProviderCacheEnabled, value -> cfg.fontBitmapProviderCacheEnabled = value, () -> defaults.fontBitmapProviderCacheEnabled);
		booleanRow("Atlas", "Atlas cap", "Cap oversized sprite frames before atlas stitching to prevent atlas overflow.",
			() -> cfg.atlasCapEnabled, value -> cfg.atlasCapEnabled = value, () -> defaults.atlasCapEnabled);
		intRow("Atlas", "Atlas cap pixels", "Maximum sprite frame size when atlas cap is enabled.",
			() -> cfg.atlasCapPx, value -> cfg.atlasCapPx = clamp(value, 16, 8192), () -> defaults.atlasCapPx);
		textRow("Atlas", "Atlas exclude ids", "Comma-separated atlas ids excluded from sprite capping.",
			() -> String.join(", ", cfg.atlasExcludeIds), () -> String.join(", ", defaults.atlasExcludeIds));
		booleanRow("Atlas", "Atlas retry", "Experimental retry path for atlas overflow recovery.",
			() -> cfg.atlasRetryEnabled, value -> cfg.atlasRetryEnabled = value, () -> defaults.atlasRetryEnabled);
		intRow("Atlas", "Atlas retry attempts", "Maximum atlas retry attempts.",
			() -> cfg.atlasRetryMaxAttempts, value -> cfg.atlasRetryMaxAttempts = clamp(value, 1, 10), () -> defaults.atlasRetryMaxAttempts);
		booleanRow("Atlas", "Disable retry with Iris", "Force atlas retry off when Iris is installed, unless you want to test risky behavior.",
			() -> cfg.forceDisablePartIIIWithIris, value -> cfg.forceDisablePartIIIWithIris = value, () -> defaults.forceDisablePartIIIWithIris);
		booleanRow("Logging", "Loader timings", "Debug log for PackForge resource index counters. Development use.",
			() -> cfg.loaderTimingsEnabled, value -> cfg.loaderTimingsEnabled = value, () -> defaults.loaderTimingsEnabled);
		booleanRow("Logging", "Reload listener timings", "Debug loading summary and CSV listener timings. Development use.",
			() -> cfg.reloadListenerTimingsEnabled, value -> cfg.reloadListenerTimingsEnabled = value, () -> defaults.reloadListenerTimingsEnabled);
		booleanRow("Logging", "Font reload diagnostics", "Debug font provider counts and CSV font timings. Development use.",
			() -> cfg.fontReloadDiagnosticsEnabled, value -> cfg.fontReloadDiagnosticsEnabled = value, () -> defaults.fontReloadDiagnosticsEnabled);
	}

	private void booleanRow(String section, String name, String description, BooleanSupplier getter, BoolSetter setter, BooleanSupplier defaultGetter) {
		rows.add(new RowSpec(section, name, description, () -> {
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

	private void intRow(String section, String name, String description, IntSupplier getter, IntConsumer setter, IntSupplier defaultGetter) {
		rows.add(new RowSpec(section, name, description, () -> {
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

	private void textRow(String section, String name, String description, Supplier<String> getter, Supplier<String> defaultGetter) {
		rows.add(new RowSpec(section, name, description, () -> {
			EditBox box = new EditBox(this.font, 0, 0, 180, 20, Component.literal(name));
			box.setMaxLength(512);
			box.setValue(getter.get());
			box.setTooltip(Tooltip.create(Component.literal(description)));
			box.setResponder(value -> {
				cfg.atlasExcludeIds = parseList(value);
				PackForgeConfig.save();
			});
			return box;
		}, () -> {
			cfg.atlasExcludeIds = parseList(defaultGetter.get());
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
		cfg.loaderIndexEnabled = defaults.loaderIndexEnabled;
		cfg.loaderZipPoolEnabled = defaults.loaderZipPoolEnabled;
		cfg.loaderTimingsEnabled = defaults.loaderTimingsEnabled;
		cfg.reloadListenerTimingsEnabled = defaults.reloadListenerTimingsEnabled;
		cfg.loadingStatusOverlayEnabled = defaults.loadingStatusOverlayEnabled;
		cfg.modelUvTransparencyClampEnabled = defaults.modelUvTransparencyClampEnabled;
		cfg.fontReloadDiagnosticsEnabled = defaults.fontReloadDiagnosticsEnabled;
		cfg.fontPrepareProviderSelectionEnabled = defaults.fontPrepareProviderSelectionEnabled;
		cfg.fontBitmapProviderCacheEnabled = defaults.fontBitmapProviderCacheEnabled;
		cfg.atlasCapEnabled = defaults.atlasCapEnabled;
		cfg.atlasCapPx = defaults.atlasCapPx;
		cfg.atlasExcludeIds = new ArrayList<>(defaults.atlasExcludeIds);
		cfg.atlasRetryEnabled = defaults.atlasRetryEnabled;
		cfg.atlasRetryMaxAttempts = defaults.atlasRetryMaxAttempts;
		cfg.forceDisablePartIIIWithIris = defaults.forceDisablePartIIIWithIris;
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

	@FunctionalInterface
	private interface BoolSetter {
		void accept(boolean value);
	}

	private record RowSpec(String section, String name, String description, Supplier<AbstractWidget> controlFactory, Runnable reset) {
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

		SectionEntry(String name, net.minecraft.client.gui.Font font) {
			this.label = new StringWidget(0, 0, 260, 20, Component.literal(name), font);
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
			label.setX(getContentX());
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
