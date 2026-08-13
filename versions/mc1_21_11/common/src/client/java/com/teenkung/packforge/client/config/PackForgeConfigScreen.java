package com.teenkung.packforge.client.config;

import net.minecraft.client.gui.screens.Screen;

/** 1.21.11 adapter for the shared native configuration renderer. */
public final class PackForgeConfigScreen extends PackForgeConfigScreenBase {
	public PackForgeConfigScreen(Screen parent) {
		super(parent);
	}

	@Override
	protected void rebuild() {
		init();
	}
}
