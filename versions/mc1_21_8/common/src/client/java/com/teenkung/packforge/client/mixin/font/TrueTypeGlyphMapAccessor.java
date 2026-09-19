package com.teenkung.packforge.client.mixin.font;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.teenkung.packforge.client.font.FontProviderGlyphMapAccess;
import net.minecraft.client.gui.font.CodepointMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.8's TTF provider exposes a bounded glyph map for worker-side counting. */
@Mixin(TrueTypeGlyphProvider.class)
public interface TrueTypeGlyphMapAccessor extends FontProviderGlyphMapAccess {
	@Override
	@Accessor("glyphs")
	CodepointMap<?> packforge$glyphMap();
}
