package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
	@WrapMethod(method = "apply")
	private void packforge$apply(@Coerce Object preparation, ProfilerFiller profiler, Operation<Void> original) {
		FontManagerPreparationAccessor accessor = (FontManagerPreparationAccessor)preparation;
		FontReloadDiagnostics.startApply(accessor.packforge$providers());
		try {
			original.call(preparation, profiler);
		} finally {
			FontReloadDiagnostics.finishApply();
		}
	}
}
