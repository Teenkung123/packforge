package com.teenkung.packforge.forge;

import net.minecraftforge.fml.ModList;

import java.util.Optional;

public final class ForgeModListCompat {
	private ForgeModListCompat() {
	}

	public static boolean isLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	public static Optional<String> version(String modId) {
		return ModList.get().getModContainerById(modId)
			.map(container -> String.valueOf(container.getModInfo().getVersion()));
	}
}
