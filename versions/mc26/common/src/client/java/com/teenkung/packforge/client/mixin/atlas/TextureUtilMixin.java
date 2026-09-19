package com.teenkung.packforge.client.mixin.atlas;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.teenkung.packforge.client.atlas.AtlasLoadInvocation;
import com.teenkung.packforge.client.atlas.SolidifyKernel;
import com.teenkung.packforge.client.atlas.SolidifyQueueWorkspace;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves solidify's exact FIFO tie ordering using bounded CPU workspace. */
@Mixin(TextureUtil.class)
public abstract class TextureUtilMixin {
	@WrapMethod(method = "solidify")
	private static void packforge$accountSolidifyQueue(NativeImage image, Operation<Void> original) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		AtlasLoadInvocation invocation = AtlasLoadInvocation.current();
		PreparationBudget.Scope budget = context != null && context.features().atlasMipParallelEnabled()
			&& !context.fontPreparationPending()
			&& invocation != null && !invocation.resourcePackUnboundedOwner()
			&& image != null && image.getClass() == NativeImage.class
			? context.preparationBudget() : null;
		if (budget == null) {
			try (SolidifyQueueWorkspace ignored = SolidifyQueueWorkspace.open(null, 0, 0)) {
				original.call(image);
			}
			return;
		}
		if (SolidifyKernel.solidify(image, budget)) return;
		try (SolidifyQueueWorkspace ignored = SolidifyQueueWorkspace.open(budget, image.getWidth(), image.getHeight())) {
			original.call(image);
		}
	}

	@WrapOperation(method = "solidify", at = @At(value = "NEW", target = "()Lit/unimi/dsi/fastutil/ints/IntArrayFIFOQueue;"))
	private static IntArrayFIFOQueue packforge$boundedSolidifyQueue(Operation<IntArrayFIFOQueue> original) {
		return SolidifyQueueWorkspace.newQueue(original::call);
	}
}
