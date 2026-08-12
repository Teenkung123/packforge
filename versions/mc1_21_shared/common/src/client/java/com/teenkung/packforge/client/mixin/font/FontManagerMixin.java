package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.teenkung.packforge.client.font.FontPreparationBundle;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
	@WrapMethod(method = "apply")
	private void packforge$apply(
		@Coerce Object preparation,
		ProfilerFiller profiler,
		Operation<Void> original
	) {
		FontSelectionRegistry.beginApply(preparation);
		FontReloadDiagnostics.startApply();
		try {
			original.call(preparation, profiler);
		} finally {
			FontPreparationBundle bundle = FontSelectionRegistry.currentBundle();
			FontReloadDiagnostics.finishApply(bundle);
			FontSelectionRegistry.clear();
		}
	}
}
