package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeCapabilities;
import com.teenkung.packforge.config.PackForgeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Searchable, scrollable native 1.20.1 renderer for the shared config model. */
public final class PackForgeConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 28;
	private static final int CONTROL_WIDTH = 120;
	private static final int RESET_WIDTH = 56;

	private final Screen parent;
	private final PackForgeConfigDraft draft = new PackForgeConfigDraft();
	private final List<PackForgeConfigScreenModel.OptionSpec> availableOptions = PackForgeConfigScreenModel.availableOptions();
	private final List<PackForgeConfigScreenModel.Category> availableCategories =
		PackForgeConfigScreenModel.availableCategories(PackForgeCapabilities.available());
	private final Map<PackForgeConfigScreenModel.OptionSpec, Boolean> invalidInputs = new HashMap<>();
	private PackForgeConfigScreenModel.Category activeCategory;
	private EditBox searchBox;
	private Button doneButton;
	private String filter = "";
	private String saveError = "";
	private int firstVisible;
	private int visibleRows;
	private int filteredCount;

	public PackForgeConfigScreen(Screen parent) {
		super(Component.translatable("packforge.config.title"));
		this.parent = parent;
		this.activeCategory = this.availableCategories.isEmpty() ? null : this.availableCategories.get(0);
	}

	@Override
	protected void init() {
		this.searchBox = new EditBox(this.font, this.width / 2 - 150, 24, 300, 20,
			Component.translatable("packforge.config.search"));
		this.searchBox.setHint(Component.translatable("packforge.config.search"));
		this.searchBox.setValue(this.filter);
		this.searchBox.setResponder(value -> {
			this.filter = value.toLowerCase(Locale.ROOT).trim();
			this.firstVisible = 0;
			rebuild();
		});
		addRenderableWidget(this.searchBox);
		addCategoryButtons();

		int contentTop = 78;
		int contentBottom = this.height - 42;
		this.visibleRows = Math.max(1, (contentBottom - contentTop) / ROW_HEIGHT);
		List<PackForgeConfigScreenModel.OptionSpec> filtered = filteredOptions();
		this.filteredCount = filtered.size();
		this.firstVisible = Math.max(0, Math.min(this.firstVisible, Math.max(0, filtered.size() - this.visibleRows)));

		int contentWidth = Math.min(760, this.width - 40);
		int left = (this.width - contentWidth) / 2;
		int end = Math.min(filtered.size(), this.firstVisible + this.visibleRows);
		for (int index = this.firstVisible; index < end; index++) {
			addOptionRow(filtered.get(index), left, contentTop + (index - this.firstVisible) * ROW_HEIGHT, contentWidth);
		}
		if (filtered.isEmpty()) {
			addRenderableWidget(new StringWidget(left, contentTop, contentWidth, 20,
				Component.translatable("packforge.config.empty"), this.font));
		}

		addRenderableWidget(Button.builder(Component.translatable("packforge.config.reset_all"), button -> {
			this.draft.resetAll(this.availableOptions);
			this.invalidInputs.clear();
			rebuild();
		}).bounds(this.width / 2 - 154, this.height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("packforge.config.cancel"), button -> closeWithoutSaving())
			.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
		this.doneButton = addRenderableWidget(Button.builder(Component.translatable("packforge.config.done"), button -> apply())
			.bounds(this.width / 2 + 54, this.height - 28, 100, 20).build());
		updateDoneButton();
	}

	private void addCategoryButtons() {
		if (this.availableCategories.isEmpty()) {
			return;
		}
		int buttonWidth = Math.min(150, (this.width - 36) / this.availableCategories.size());
		int totalWidth = buttonWidth * this.availableCategories.size() + 6 * (this.availableCategories.size() - 1);
		int startX = (this.width - totalWidth) / 2;
		for (int index = 0; index < this.availableCategories.size(); index++) {
			PackForgeConfigScreenModel.Category category = this.availableCategories.get(index);
			Component label = Component.translatable(category.translationKey())
				.withStyle(category == this.activeCategory ? ChatFormatting.GREEN : ChatFormatting.WHITE);
			addRenderableWidget(Button.builder(label, button -> {
				this.activeCategory = category;
				this.firstVisible = 0;
				rebuild();
			}).bounds(startX + index * (buttonWidth + 6), 50, buttonWidth, 20).build());
		}
	}

	private List<PackForgeConfigScreenModel.OptionSpec> filteredOptions() {
		List<PackForgeConfigScreenModel.OptionSpec> result = new ArrayList<>();
		for (PackForgeConfigScreenModel.OptionSpec option : this.availableOptions) {
			if (option.category() == this.activeCategory && matches(option)) {
				result.add(option);
			}
		}
		return result;
	}

	private boolean matches(PackForgeConfigScreenModel.OptionSpec option) {
		if (this.filter.isEmpty()) {
			return true;
		}
		return option.id().contains(this.filter)
			|| option.section().contains(this.filter)
			|| Component.translatable(option.titleKey()).getString().toLowerCase(Locale.ROOT).contains(this.filter)
			|| Component.translatable(option.descriptionKey()).getString().toLowerCase(Locale.ROOT).contains(this.filter);
	}

	private void addOptionRow(PackForgeConfigScreenModel.OptionSpec option, int left, int y, int contentWidth) {
		int labelWidth = Math.max(1, contentWidth - CONTROL_WIDTH - RESET_WIDTH - 12);
		Component labelText = Component.translatable(option.sectionKey()).append(": ")
			.append(Component.translatable(option.titleKey()));
		StringWidget label = new StringWidget(left, y, labelWidth, 20, labelText, this.font);
		label.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey()).append("\n")
			.append(Component.translatable(option.applyScope().translationKey()))));
		addRenderableWidget(label);

		AbstractWidget control = createControl(option, left + labelWidth + 4, y);
		addRenderableWidget(control);
		Button reset = Button.builder(Component.translatable("packforge.config.reset"), button -> {
			this.draft.reset(option);
			this.invalidInputs.remove(option);
			rebuild();
		}).bounds(left + contentWidth - RESET_WIDTH, y, RESET_WIDTH, 20).build();
		reset.active = !option.sameValue(this.draft.working(), new PackForgeConfig.Cfg());
		addRenderableWidget(reset);
	}

	private AbstractWidget createControl(PackForgeConfigScreenModel.OptionSpec spec, int x, int y) {
		if (spec instanceof PackForgeConfigScreenModel.BooleanOption option) {
			Button button = Button.builder(booleanText(option.get(this.draft.working())), widget -> {
				boolean value = !option.get(this.draft.working());
				option.set(this.draft.working(), value);
				widget.setMessage(booleanText(value));
			}).bounds(x, y, CONTROL_WIDTH, 20).build();
			button.setTooltip(Tooltip.create(Component.translatable(spec.descriptionKey())));
			return button;
		}
		if (spec instanceof PackForgeConfigScreenModel.IntegerOption option) {
			EditBox box = new EditBox(this.font, x, y, CONTROL_WIDTH, 20, Component.translatable(spec.titleKey()));
			box.setValue(Integer.toString(option.get(this.draft.working())));
			box.setTooltip(Tooltip.create(Component.translatable(spec.descriptionKey())));
			box.setResponder(value -> updateInteger(option, box, value));
			return box;
		}
		PackForgeConfigScreenModel.StringListOption option = (PackForgeConfigScreenModel.StringListOption)spec;
		EditBox box = new EditBox(this.font, x, y, CONTROL_WIDTH, 20, Component.translatable(spec.titleKey()));
		box.setMaxLength(512);
		box.setValue(option.format(this.draft.working()));
		box.setTooltip(Tooltip.create(Component.translatable(spec.descriptionKey())));
		box.setResponder(value -> option.set(this.draft.working(), value));
		return box;
	}

	private void updateInteger(PackForgeConfigScreenModel.IntegerOption option, EditBox box, String value) {
		try {
			int parsed = Integer.parseInt(value.trim());
			if (!option.valid(parsed)) {
				setInvalid(option, box);
				return;
			}
			option.set(this.draft.working(), parsed);
			box.setTextColor(0xE0E0E0);
			box.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey())));
			this.invalidInputs.remove(option);
		} catch (NumberFormatException exception) {
			setInvalid(option, box);
		}
		updateDoneButton();
	}

	private void setInvalid(PackForgeConfigScreenModel.IntegerOption option, EditBox box) {
		this.invalidInputs.put(option, true);
		box.setTextColor(0xFF5555);
		box.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey()).append("\n")
			.append(Component.literal(option.minimum() + "-" + option.maximum()).withStyle(ChatFormatting.RED))));
		updateDoneButton();
	}

	private void apply() {
		if (!this.invalidInputs.isEmpty()) {
			return;
		}
		PackForgeConfig.SaveResult result = this.draft.apply();
		if (result.successful()) {
			Minecraft.getInstance().setScreen(this.parent);
			return;
		}
		this.saveError = result.errorMessage();
		rebuild();
	}

	private void closeWithoutSaving() {
		this.draft.discard();
		Minecraft.getInstance().setScreen(this.parent);
	}

	@Override
	public void onClose() {
		closeWithoutSaving();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		int maximum = Math.max(0, this.filteredCount - this.visibleRows);
		int next = Math.max(0, Math.min(maximum, this.firstVisible + (delta > 0.0 ? -1 : 1)));
		if (next != this.firstVisible) {
			this.firstVisible = next;
			rebuild();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		if (this.filteredCount > this.visibleRows) {
			String range = (this.firstVisible + 1) + "-" + Math.min(this.filteredCount, this.firstVisible + this.visibleRows)
				+ " / " + this.filteredCount;
			graphics.drawString(this.font, range, this.width - this.font.width(range) - 12, 56, 0xFFAAAAAA, false);
		}
		if (!this.saveError.isEmpty()) {
			graphics.drawCenteredString(this.font, Component.translatable("packforge.config.save_error", this.saveError),
				this.width / 2, this.height - 42, 0xFFFF5555);
		}
	}

	private void updateDoneButton() {
		if (this.doneButton != null) {
			this.doneButton.active = this.invalidInputs.isEmpty();
		}
	}

	private Component booleanText(boolean value) {
		return Component.translatable(value ? "packforge.config.on" : "packforge.config.off")
			.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private void rebuild() {
		init(Minecraft.getInstance(), this.width, this.height);
	}
}
