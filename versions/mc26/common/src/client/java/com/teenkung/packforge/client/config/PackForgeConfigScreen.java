package com.teenkung.packforge.client.config;

import com.teenkung.packforge.client.compat.MinecraftGuiCompat;
import com.teenkung.packforge.config.PackForgeCapabilities;
import com.teenkung.packforge.config.PackForgeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native Minecraft 26.x renderer for the version-independent config model. */
public final class PackForgeConfigScreen extends Screen {
	private final Screen parent;
	private final PackForgeConfigDraft draft = new PackForgeConfigDraft();
	private final List<PackForgeConfigScreenModel.OptionSpec> availableOptions = PackForgeConfigScreenModel.availableOptions();
	private final List<PackForgeConfigScreenModel.Category> availableCategories = PackForgeConfigScreenModel.availableCategories(PackForgeCapabilities.available());
	private final Map<PackForgeConfigScreenModel.OptionSpec, Boolean> invalidInputs = new java.util.HashMap<>();
	private EditBox searchBox;
	private ConfigList list;
	private Button doneButton;
	private String filter = "";
	private String saveError = "";
	private PackForgeConfigScreenModel.Category activeCategory;

	public PackForgeConfigScreen(Screen parent) {
		super(Component.translatable("packforge.config.title"));
		this.parent = parent;
		this.activeCategory = availableCategories.isEmpty() ? null : availableCategories.getFirst();
	}

	@Override
	protected void init() {
		searchBox = new EditBox(this.font, this.width / 2 - 150, 24, 300, 20, Component.translatable("packforge.config.search"));
		searchBox.setHint(Component.translatable("packforge.config.search"));
		searchBox.setValue(filter);
		searchBox.setResponder(value -> {
			filter = value.toLowerCase(Locale.ROOT).trim();
			rebuildList();
		});
		addRenderableWidget(searchBox);

		addCategoryButtons();
		list = new ConfigList(this.minecraft, this.width, this.height - 118, 78, 26);
		addRenderableWidget(list);
		rebuildList();

		addRenderableWidget(Button.builder(Component.translatable("packforge.config.reset_all"), button -> {
			draft.resetAll(availableOptions);
			invalidInputs.clear();
			rebuildList();
			updateDoneButton();
		}).bounds(this.width / 2 - 154, this.height - 32, 100, 20).build());
		doneButton = addRenderableWidget(Button.builder(Component.translatable("packforge.config.done"), button -> apply())
			.bounds(this.width / 2 + 54, this.height - 32, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("packforge.config.cancel"), button -> closeWithoutSaving())
			.bounds(this.width / 2 - 50, this.height - 32, 100, 20).build());
		if (!saveError.isEmpty()) {
			addRenderableOnly(new StringWidget(0, this.height - 54, this.width, 12,
				Component.translatable("packforge.config.save_error", saveError).withStyle(ChatFormatting.RED), this.font));
		}
		addRenderableOnly(new StringWidget(0, 8, this.width, 12, this.title, this.font));
		updateDoneButton();
	}

	@Override
	public void onClose() {
		closeWithoutSaving();
	}

	private void addCategoryButtons() {
		if (availableCategories.isEmpty()) {
			return;
		}
		int buttonWidth = Math.min(150, (this.width - 36) / availableCategories.size());
		int start = this.width / 2 - (buttonWidth * availableCategories.size() + 6 * (availableCategories.size() - 1)) / 2;
		for (int index = 0; index < availableCategories.size(); index++) {
			PackForgeConfigScreenModel.Category category = availableCategories.get(index);
			int x = start + index * (buttonWidth + 6);
			addRenderableWidget(Button.builder(categoryText(category), button -> {
				activeCategory = category;
				rebuildWidgets();
			}).bounds(x, 50, buttonWidth, 20).build());
		}
	}

	private Component categoryText(PackForgeConfigScreenModel.Category category) {
		return Component.translatable(category.translationKey())
			.withStyle(category == activeCategory ? ChatFormatting.GREEN : ChatFormatting.WHITE);
	}

	private void rebuildList() {
		if (list == null) {
			return;
		}
		// Rebuilding discards each EditBox's raw text. Invalid text was never
		// applied to the draft, so its validation marker must be discarded too.
		invalidInputs.clear();
		list.clear();
		String currentSection = null;
		for (PackForgeConfigScreenModel.OptionSpec option : availableOptions) {
			if (option.category() != activeCategory || !matches(option)) {
				continue;
			}
			if (!option.section().equals(currentSection)) {
				currentSection = option.section();
				list.add(new SectionEntry(Component.translatable(option.sectionKey()), this.font));
			}
			list.add(new OptionEntry(option, this.font));
		}
		updateDoneButton();
	}

	private boolean matches(PackForgeConfigScreenModel.OptionSpec option) {
		if (filter.isEmpty()) {
			return true;
		}
		return option.id().contains(filter)
			|| option.section().contains(filter)
			|| Component.translatable(option.titleKey()).getString().toLowerCase(Locale.ROOT).contains(filter)
			|| Component.translatable(option.descriptionKey()).getString().toLowerCase(Locale.ROOT).contains(filter);
	}

	private void apply() {
		if (hasInvalidInput()) {
			return;
		}
		PackForgeConfig.SaveResult result = draft.apply();
		if (result.successful()) {
			MinecraftGuiCompat.setScreen(this.minecraft, parent);
			return;
		}
		saveError = result.errorMessage();
		rebuildWidgets();
	}

	private void closeWithoutSaving() {
		draft.discard();
		MinecraftGuiCompat.setScreen(this.minecraft, parent);
	}

	private boolean hasInvalidInput() {
		return invalidInputs.values().stream().anyMatch(Boolean::booleanValue);
	}

	private void setInputValidity(PackForgeConfigScreenModel.OptionSpec option, boolean valid) {
		if (valid) {
			invalidInputs.remove(option);
		} else {
			invalidInputs.put(option, true);
		}
		updateDoneButton();
	}

	private void updateDoneButton() {
		if (doneButton != null) {
			doneButton.active = !hasInvalidInput();
		}
	}

	private Component booleanText(boolean value) {
		return Component.translatable(value ? "packforge.config.on" : "packforge.config.off")
			.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private final class OptionEntry extends ConfigEntry {
		private final PackForgeConfigScreenModel.OptionSpec option;
		private final StringWidget label;
		private final AbstractWidget control;
		private final Button reset;

		OptionEntry(PackForgeConfigScreenModel.OptionSpec option, Font font) {
			this.option = option;
			this.label = new StringWidget(0, 0, 260, 20, Component.translatable(option.titleKey()), font);
			this.label.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey()).append("\n").append(Component.translatable(option.applyScope().translationKey()))));
			this.control = createControl(option, font);
			this.reset = Button.builder(Component.translatable("packforge.config.reset"), button -> {
				draft.reset(option);
				invalidInputs.remove(option);
				rebuildList();
				updateDoneButton();
			}).size(56, 20).build();
		}

		private AbstractWidget createControl(PackForgeConfigScreenModel.OptionSpec spec, Font font) {
			if (spec instanceof PackForgeConfigScreenModel.BooleanOption option) {
				Button button = Button.builder(booleanText(option.get(draft.working())), widget -> {
					boolean value = !option.get(draft.working());
					option.set(draft.working(), value);
					widget.setMessage(booleanText(value));
				}).size(120, 20).build();
				button.setTooltip(Tooltip.create(Component.translatable(spec.descriptionKey())));
				return button;
			}
			if (spec instanceof PackForgeConfigScreenModel.IntegerOption option) {
				EditBox box = new EditBox(font, 0, 0, 120, 20, Component.translatable(spec.titleKey()));
				box.setValue(Integer.toString(option.get(draft.working())));
				box.setTooltip(Tooltip.create(Component.translatable(spec.descriptionKey())));
				box.setResponder(value -> updateInteger(option, box, value));
				return box;
			}
			PackForgeConfigScreenModel.StringListOption option = (PackForgeConfigScreenModel.StringListOption) spec;
			EditBox box = new EditBox(font, 0, 0, 180, 20, Component.translatable(spec.titleKey()));
			box.setMaxLength(512);
			box.setValue(option.format(draft.working()));
			box.setTooltip(Tooltip.create(Component.translatable(spec.descriptionKey())));
			box.setResponder(value -> option.set(draft.working(), value));
			return box;
		}

		private void updateInteger(PackForgeConfigScreenModel.IntegerOption option, EditBox box, String value) {
			try {
				int parsed = Integer.parseInt(value.trim());
				if (!option.valid(parsed)) {
					setInvalid(box, option);
					return;
				}
				option.set(draft.working(), parsed);
				box.setTextColor(0xE0E0E0);
				setInputValidity(option, true);
			} catch (NumberFormatException exception) {
				setInvalid(box, option);
			}
		}

		private void setInvalid(EditBox box, PackForgeConfigScreenModel.IntegerOption option) {
			box.setTextColor(0xFF5555);
			box.setTooltip(Tooltip.create(Component.translatable(option.descriptionKey()).append("\n")
				.append(Component.literal(option.minimum() + "-" + option.maximum()).withStyle(ChatFormatting.RED))));
			setInputValidity(option, false);
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

		SectionEntry(Component title, Font font) {
			this.label = new StringWidget(0, 0, 260, 20, title, font);
			this.textWidth = font.width(title);
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
}
