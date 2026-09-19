package com.teenkung.packforge.client.mixin.font;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Count existing entries without allocating CodepointMap.keySet() before admission. */
@Mixin({BitmapProvider.class, UnihexProvider.class, TrueTypeGlyphProvider.class})
public interface FontProviderGlyphMapAccessor {
	@Accessor("glyphs")
	CodepointMap<?> packforge$glyphMap();
}
