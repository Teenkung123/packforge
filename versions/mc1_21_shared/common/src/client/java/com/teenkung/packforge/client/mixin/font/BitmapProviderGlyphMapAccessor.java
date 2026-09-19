package com.teenkung.packforge.client.mixin.font;

import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import com.teenkung.packforge.client.font.FontProviderGlyphMapAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BitmapProvider.class)
public interface BitmapProviderGlyphMapAccessor extends FontProviderGlyphMapAccess {
	@Override @Accessor("glyphs") CodepointMap<?> packforge$glyphMap();
}
