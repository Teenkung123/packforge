package dev.packbench.correctness.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.packbench.correctness.CapturedSprite;
import dev.packbench.correctness.TextureCapture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin implements CapturedSprite {
    @Shadow private NativeImage[] byMipLevel;
    @Unique private TextureCapture.Sprite correctness$snapshot;

    // The short constructor delegates here. Hash exactly once before any mip/solidify call.
    @Inject(method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V", at = @At("RETURN"))
    private void correctness$decoded(Identifier id, FrameSize size, NativeImage image,
                                    Optional<AnimationMetadataSection> animation,
                                    List<MetadataSectionType.WithValue<?>> additional,
                                    Optional<TextureMetadataSection> texture, CallbackInfo callback) {
        correctness$snapshot = TextureCapture.created(id.toString(), size.width(), size.height(),
            ((SpriteContents) (Object) this).isAnimated(), image);
    }

    @Inject(method = "increaseMipLevel", at = @At("RETURN"))
    private void correctness$capture(int requestedLevel, CallbackInfo callback) {
        TextureCapture.capture(correctness$snapshot, "mip_ready", ((SpriteContents) (Object) this).name().toString(), requestedLevel, byMipLevel);
    }

    @Override public TextureCapture.Sprite correctness$snapshot() { return correctness$snapshot; }
    @Override public void correctness$captureUpload(String resourceId, int requestedLevel) {
        TextureCapture.capture(correctness$snapshot, "upload_input", resourceId, requestedLevel, byMipLevel);
    }
}
