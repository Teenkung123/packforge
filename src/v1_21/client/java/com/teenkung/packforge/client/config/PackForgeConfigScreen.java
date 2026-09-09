package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Paged native Reload Optimizer configuration screen for 1.21.x. */
public final class PackForgeConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 28;
	private final Screen parent;
	private final PackForgeConfigDraft draft = new PackForgeConfigDraft();
	private final List<PackForgeConfigScreenModel.OptionSpec> options = PackForgeConfigScreenModel.availableOptions().stream()
		.filter(option -> option.category() == PackForgeConfigScreenModel.Category.RELOAD).toList();
	private String filter = "";
	private int page;
	private Component saveError;
	private EditBox search;

	public PackForgeConfigScreen(Screen parent) {
		super(Component.translatable("packforge.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		clearWidgets();
		int contentWidth = Math.min(520, this.width - 24);
		int left = (this.width - contentWidth) / 2;
		search = addRenderableWidget(new EditBox(this.font, left, 28, contentWidth, 20, Component.translatable("packforge.config.search")));
		search.setHint(Component.translatable("packforge.config.search"));
		search.setValue(filter);
		search.setResponder(value -> {
			String next = value.toLowerCase(Locale.ROOT).trim();
			if (!next.equals(filter)) { filter = next; page = 0; rebuild(); }
		});
		List<PackForgeConfigScreenModel.OptionSpec> visible = filtered();
		int pageSize = PackForgeConfigScreenLayout.pageSize(this.height, saveError != null);
		int pages = Math.max(1, (visible.size() + pageSize - 1) / pageSize);
		page = Math.min(page, pages - 1);
		int from = page * pageSize;
		int to = Math.min(visible.size(), from + pageSize);
		int rowY = 58;
		for (PackForgeConfigScreenModel.OptionSpec option : visible.subList(from, to)) {
			addRow(option, left, rowY, contentWidth);
			rowY += ROW_HEIGHT;
		}
		if (visible.isEmpty()) addRenderableWidget(new StringWidget(left, rowY, contentWidth, 20, Component.translatable("packforge.config.empty"), this.font));
		addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuild(); }).bounds(left, this.height - 60, 24, 20).build()).active = page > 0;
		addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuild(); }).bounds(left + 28, this.height - 60, 24, 20).build()).active = page + 1 < pages;
		addRenderableWidget(new StringWidget(left + 58, this.height - 60, 100, 20, Component.literal((page + 1) + "/" + pages), this.font));
		int action = Math.min(120, (contentWidth - 12) / 3);
		addRenderableWidget(Button.builder(Component.translatable("packforge.config.button.reset_all"), button -> { draft.resetAll(options); saveError = null; rebuild(); }).bounds(left, this.height - 32, action, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("packforge.config.cancel"), button -> onClose()).bounds(left + (contentWidth - action) / 2, this.height - 32, action, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("packforge.config.button.done"), button -> apply()).bounds(left + contentWidth - action, this.height - 32, action, 20).build());
	}

	private List<PackForgeConfigScreenModel.OptionSpec> filtered() {
		if (filter.isEmpty()) return options;
		return options.stream().filter(option -> option.id().contains(filter) || option.section().contains(filter)
			|| Component.translatable(option.titleKey()).getString().toLowerCase(Locale.ROOT).contains(filter)
			|| Component.translatable(option.descriptionKey()).getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
	}

	private void addRow(PackForgeConfigScreenModel.OptionSpec option, int left, int y, int width) {
		int resetWidth = 54;
		int controlWidth = option instanceof PackForgeConfigScreenModel.StringListOption ? 170 : 104;
		StringWidget label = new StringWidget(left, y, Math.max(1, width - controlWidth - resetWidth - 8), 20, Component.translatable(option.titleKey()), this.font);
		label.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey()).append("\n").append(Component.translatable(option.applyScope().translationKey()))));
		addRenderableWidget(label);
		int controlX = left + width - controlWidth - resetWidth - 4;
		if (option instanceof PackForgeConfigScreenModel.BooleanOption bool) {
			addRenderableWidget(Button.builder(boolText(bool.get(draft.working())), button -> { boolean value = !bool.get(draft.working()); bool.set(draft.working(), value); button.setMessage(boolText(value)); }).bounds(controlX, y, controlWidth, 20).tooltip(Tooltip.create(Component.translatable(option.descriptionKey()))).build());
		} else if (option instanceof PackForgeConfigScreenModel.IntegerOption integer) {
			EditBox box = addRenderableWidget(new EditBox(this.font, controlX, y, controlWidth, 20, Component.translatable(option.titleKey())));
			box.setValue(Integer.toString(integer.get(draft.working())));
			box.setResponder(value -> updateInteger(integer, box, value));
			box.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey())));
		} else {
			PackForgeConfigScreenModel.StringListOption list = (PackForgeConfigScreenModel.StringListOption) option;
			EditBox box = addRenderableWidget(new EditBox(this.font, controlX, y, controlWidth, 20, Component.translatable(option.titleKey())));
			box.setValue(list.format(draft.working())); box.setMaxLength(512); box.setResponder(value -> list.set(draft.working(), value));
			box.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey())));
		}
		Button reset = addRenderableWidget(Button.builder(Component.translatable("packforge.config.button.reset"), button -> { draft.reset(option); rebuild(); }).bounds(left + width - resetWidth, y, resetWidth, 20).build());
		reset.active = !option.sameValue(draft.working(), new PackForgeConfig.Cfg());
	}

	private void updateInteger(PackForgeConfigScreenModel.IntegerOption option, EditBox box, String value) {
		try { int parsed = Integer.parseInt(value.trim()); if (!option.valid(parsed)) throw new NumberFormatException(); option.set(draft.working(), parsed); box.setTextColor(0xE0E0E0); }
		catch (NumberFormatException ignored) { box.setTextColor(0xFF5555); box.setTooltip(Tooltip.create(Component.literal(option.minimum() + "-" + option.maximum()))); }
	}

	private void apply() { PackForgeConfig.SaveResult result = draft.apply(); if (result.successful()) Minecraft.getInstance().setScreen(parent); else { saveError = Component.translatable("packforge.config.save_failed", result.errorMessage()); rebuild(); } }
	@Override public void onClose() { draft.discard(); Minecraft.getInstance().setScreen(parent); }
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xB0101010);
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
		if (saveError != null) {
			graphics.drawCenteredString(this.font, saveError, this.width / 2, this.height - 84, 0xFF5555);
		}
	}
	private void rebuild() { init(Minecraft.getInstance(), this.width, this.height); }
	private static Component boolText(boolean value) { return Component.translatable(value ? "packforge.config.value.enabled" : "packforge.config.value.disabled"); }
}
