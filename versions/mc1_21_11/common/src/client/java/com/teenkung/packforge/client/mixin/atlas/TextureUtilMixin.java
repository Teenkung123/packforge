package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.teenkung.packforge.client.atlas.SolidifyKernel;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.platform.OptimizationCompatibility;
import org.spongepowered.asm.mixin.Mixin;

/** Uses bounded CPU solidify only after authoritative ownership and reload admission. */
@Mixin(TextureUtil.class)
public abstract class TextureUtilMixin {
	@WrapMethod(method = "solidify")
	private static void packforge$boundedSolidify(NativeImage image, Operation<Void> original) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		OptimizationCompatibility.State compatibility = OptimizationCompatibility.current();
		PreparationBudget.Scope budget = context != null
			&& context.features().atlasMipParallelEnabled()
			&& !compatibility.quickPackPresent()
			&& !context.fontPreparationPending()
			&& image != null && image.getClass() == NativeImage.class
			? context.preparationBudget() : null;
		if (budget == null || !SolidifyKernel.solidify(image, budget)) {
			original.call(image);
		}
	}
}
