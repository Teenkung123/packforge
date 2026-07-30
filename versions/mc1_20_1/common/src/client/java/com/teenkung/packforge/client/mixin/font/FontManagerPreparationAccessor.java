package com.teenkung.packforge.client.mixin.font;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(targets = "net.minecraft.client.gui.font.FontManager$Preparation")
public interface FontManagerPreparationAccessor {
	@Accessor("providers")
	Map<ResourceLocation, List<GlyphProvider>> packforge$providers();
}
