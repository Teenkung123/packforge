package dev.packbench.correctness.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.packbench.correctness.CapturedSprite;
import dev.packbench.correctness.TextureCapture;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
    @Shadow @Final private Identifier location;

    @WrapMethod(method = "upload")
    private void correctness$upload(SpriteLoader.Preparations preparations, Operation<Void> original) {
        if (!TextureCapture.ENABLED) { original.call(preparations); return; }
        TextureCapture.Context context = null;
        for (var entry : preparations.regions().entrySet()) {
            CapturedSprite sprite = (CapturedSprite) entry.getValue().contents();
            TextureCapture.Context owner = TextureCapture.uploadContext(location.toString(), sprite.correctness$snapshot());
            if (owner != null) {
                if (context != null && !context.equals(owner)) TextureCapture.invalidate("mixed atlas upload generations or owners");
                context = owner;
            }
            sprite.correctness$captureUpload(entry.getKey().toString(), preparations.mipLevel());
        }
        if (context == null) TextureCapture.invalidate("atlas upload has no captured sprite ownership");
        TextureCapture.Context captured = context;
        try {
            // Iris PBR work attached to upload retains this atlas context, without changing its dispatch.
            TextureCapture.within(captured, () -> { original.call(preparations); return null; });
            TextureCapture.uploaded(captured, preparations.regions().size());
        } catch (RuntimeException | Error failure) {
            TextureCapture.invalidate("atlas upload failed: " + failure.getClass().getName());
            throw failure;
        }
    }
}
