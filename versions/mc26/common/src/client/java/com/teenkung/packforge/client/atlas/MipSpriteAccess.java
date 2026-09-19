package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;

/** Internal ownership inspection only; the caller must never modify or retain this array. */
public interface MipSpriteAccess {
    NativeImage[] packforge$mipImages();
}
