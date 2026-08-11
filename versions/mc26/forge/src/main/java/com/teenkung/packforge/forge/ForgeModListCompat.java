package com.teenkung.packforge.forge;

import net.minecraftforge.fml.ModList;

import java.util.Optional;

final class ForgeModListCompat {
	private ForgeModListCompat() {
	}

	static boolean isLoaded(String modId) {
		return ModList.isLoaded(modId);
	}

	static Optional<String> version(String modId) {
		return ModList.getModContainerById(modId)
			.map(container -> String.valueOf(container.getModInfo().getVersion()));
	}
}
