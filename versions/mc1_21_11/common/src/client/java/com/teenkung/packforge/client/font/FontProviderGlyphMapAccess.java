package com.teenkung.packforge.client.font;

import net.minecraft.client.gui.font.CodepointMap;

/** Consumer-side contract implemented by target-specific glyph-map accessors. */
public interface FontProviderGlyphMapAccess {
	CodepointMap<?> packforge$glyphMap();
}
