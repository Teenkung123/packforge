package com.teenkung.packforge.forge;

import net.minecraftforge.fml.ModList;

final class ForgeModListCompat {
	private ForgeModListCompat() {
	}

	static boolean isLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
}
