package com.teenkung.packforge.client.mixin.config;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.config.PackForgeConfigScreen;
import com.teenkung.packforge.client.config.ResourcePackButtonLayout;
import com.teenkung.packforge.client.config.ResourcePackButtonLayoutTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Adds PackForge's collision-aware configuration shortcut to the client resource-pack screen. */
@Mixin(value = PackSelectionScreen.class, priority = 100)
public abstract class PackSelectionScreenMixin extends Screen {
	@Unique
	private static final ResourceLocation PACKFORGE_CONFIG_COG = ResourceLocation.tryBuild(
		"packforge",
		"textures/gui/sprites/config_cog.png"
	);
	@Shadow @Final private Path packDir;
	@Shadow private Button doneButton;
	@Unique private Button packforge$configButton;
	@Unique private boolean packforge$loggedNoSpace;
	@Unique private ResourcePackButtonLayoutTracker packforge$layoutTracker;

	protected PackSelectionScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void packforge$addConfigButton(CallbackInfo callbackInfo) {
		if (this.minecraft == null || !this.packDir.equals(this.minecraft.getResourcePackDirectory())) {
			return;
		}
		this.packforge$configButton = this.addRenderableWidget(new PackForgeConfigButton(
			this.doneButton.getX() + this.doneButton.getWidth() + 8,
			this.doneButton.getY(),
			button -> this.minecraft.setScreen(new PackForgeConfigScreen((Screen) (Object) this))
		));
		this.packforge$configButton.setTooltip(Tooltip.create(
			Component.translatable("packforge.config.resource_pack_button")
		));
		this.packforge$configButton.visible = false;
		this.packforge$layoutTracker = new ResourcePackButtonLayoutTracker();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void packforge$detectLateConfigButtons(CallbackInfo callbackInfo) {
		packforge$refreshConfigButtonPlacement();
	}

	@Unique
	private void packforge$refreshConfigButtonPlacement() {
		if (this.packforge$configButton == null || this.packforge$layoutTracker == null) {
			return;
		}
		long signature = ResourcePackButtonLayoutTracker.beginSignature(this.width, this.height);
		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget && widget != this.packforge$configButton) {
				signature = ResourcePackButtonLayoutTracker.includeWidget(
					signature,
					System.identityHashCode(widget),
					widget.getX(),
					widget.getY(),
					widget.getWidth(),
					widget.getHeight(),
					widget.visible
				);
			}
		}
		if (this.packforge$layoutTracker.shouldReflow(signature, packforge$buttonCollides())) {
			packforge$placeConfigButton();
		}
	}

	@Unique
	private boolean packforge$buttonCollides() {
		if (!this.packforge$configButton.visible) {
			return false;
		}
		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget
				&& widget != this.packforge$configButton
				&& widget.visible
				&& packforge$overlaps(this.packforge$configButton, widget)) {
				return true;
			}
		}
		return false;
	}

	@Unique
	private void packforge$placeConfigButton() {
		if (this.packforge$configButton == null || this.doneButton == null) {
			return;
		}
		ResourcePackButtonLayout.Rectangle done = packforge$rectangle(this.doneButton);
		ResourcePackButtonLayout.Rectangle openFolder = this.children().stream()
			.filter(AbstractWidget.class::isInstance)
			.map(AbstractWidget.class::cast)
			.filter(widget -> widget != this.packforge$configButton && widget != this.doneButton)
			.filter(widget -> widget.visible)
			.filter(widget -> packforge$overlapsVertically(widget, this.doneButton))
			.filter(widget -> widget.getX() < this.doneButton.getX())
			.max(Comparator.comparingInt(AbstractWidget::getWidth))
			.map(this::packforge$rectangle)
			.orElseGet(() -> new ResourcePackButtonLayout.Rectangle(Math.max(4, this.width - this.doneButton.getX() - this.doneButton.getWidth()), this.doneButton.getY(), this.doneButton.getWidth(), this.doneButton.getHeight()));
		List<ResourcePackButtonLayout.Rectangle> occupied = new ArrayList<>();
		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget && widget != this.packforge$configButton && widget.visible) {
				occupied.add(packforge$rectangle(widget));
			}
		}
		ResourcePackButtonLayout.findPlacement(new ResourcePackButtonLayout.Rectangle(4, 4, Math.max(0, this.width - 8), Math.max(0, this.height - 8)), new ResourcePackButtonLayout.ActionButtonAnchors(openFolder, done), this.packforge$configButton.getWidth(), this.packforge$configButton.getHeight(), 8, 4, occupied).ifPresentOrElse(position -> {
			this.packforge$configButton.setX(position.x());
			this.packforge$configButton.setY(position.y());
			this.packforge$configButton.visible = true;
		}, () -> {
			this.packforge$configButton.visible = false;
			if (!this.packforge$loggedNoSpace) {
				this.packforge$loggedNoSpace = true;
				PackForge.LOGGER.debug("PackForge config button hidden because the resource-pack screen has no free action slot");
			}
		});
	}

	@Unique
	private boolean packforge$overlapsVertically(AbstractWidget first, AbstractWidget second) {
		return first.getY() < second.getY() + second.getHeight() && first.getY() + first.getHeight() > second.getY();
	}

	@Unique
	private boolean packforge$overlaps(AbstractWidget first, AbstractWidget second) {
		return first.getX() < second.getX() + second.getWidth()
			&& first.getX() + first.getWidth() > second.getX()
			&& packforge$overlapsVertically(first, second);
	}

	@Unique
	private ResourcePackButtonLayout.Rectangle packforge$rectangle(AbstractWidget widget) {
		return new ResourcePackButtonLayout.Rectangle(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
	}

	@Unique
	private static final class PackForgeConfigButton extends Button {
		private PackForgeConfigButton(int x, int y, OnPress onPress) {
			super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
			graphics.blit(PACKFORGE_CONFIG_COG, getX() + 2, getY() + 2, 0.0F, 0.0F, 16, 16, 16, 16);
		}
	}
}
