package com.teenkung.packforge.client.mixin.font;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.teenkung.packforge.client.font.FontProviderGlyphMapAccess;
import net.minecraft.client.gui.font.CodepointMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TrueTypeGlyphProvider.class)
public interface TrueTypeGlyphMapAccessor extends FontProviderGlyphMapAccess {
	@Override @Accessor("glyphs") CodepointMap<?> packforge$glyphMap();
}
