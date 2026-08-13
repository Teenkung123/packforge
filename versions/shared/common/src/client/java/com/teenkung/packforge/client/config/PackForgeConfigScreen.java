package com.teenkung.packforge.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 1.20.5-1.21.10 adapter for the shared native configuration renderer. */
public final class PackForgeConfigScreen extends PackForgeConfigScreenBase {
	public PackForgeConfigScreen(Screen parent) {
		super(parent);
	}

	@Override
	protected void rebuild() {
		init(Minecraft.getInstance(), this.width, this.height);
	}
}
