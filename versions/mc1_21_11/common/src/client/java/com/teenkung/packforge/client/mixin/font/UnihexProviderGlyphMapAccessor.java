package com.teenkung.packforge.client.mixin.font;

import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import com.teenkung.packforge.client.font.FontProviderGlyphMapAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UnihexProvider.class)
public interface UnihexProviderGlyphMapAccessor extends FontProviderGlyphMapAccess {
	@Override @Accessor("glyphs") CodepointMap<?> packforge$glyphMap();
}
