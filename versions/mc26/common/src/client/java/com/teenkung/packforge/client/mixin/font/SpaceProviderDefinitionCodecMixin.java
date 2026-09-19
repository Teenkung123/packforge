package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import com.teenkung.packforge.client.font.SpaceAdvanceCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(SpaceProvider.Definition.class)
public abstract class SpaceProviderDefinitionCodecMixin {
	@WrapOperation(method = "lambda$static$0", at = @At(value = "INVOKE",
		target = "Lcom/mojang/serialization/codecs/UnboundedMapCodec;fieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;"))
	private static MapCodec<Map<Integer, Float>> packforge$boundedAdvanceDecoding(UnboundedMapCodec<Integer, Float> codec,
		String field, Operation<MapCodec<Map<Integer, Float>>> original) {
		return SpaceAdvanceCodec.wrap(codec, field, original.call(codec, field));
	}
}
