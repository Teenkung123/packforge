package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.loader.PackIndexCache;
import com.teenkung.packforge.loader.ZipFilePools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess")
public abstract class SharedZipFileAccessMixin {
	@Inject(method = "close", at = @At("TAIL"))
	private void packforge$invalidateCache(CallbackInfo ci) {
		Object access = this;
		PackIndexCache.invalidate(access);
		ZipFilePools.close(access);
	}
}
