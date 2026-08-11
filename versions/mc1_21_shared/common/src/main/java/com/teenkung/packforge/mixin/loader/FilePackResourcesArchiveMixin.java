package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.internal.loader.FilePackResourcesArchiveHolder;
import com.teenkung.packforge.internal.loader.SharedZipFileAccessBridge;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesArchiveMixin {
	@Inject(
		method = "<init>(Lnet/minecraft/server/packs/PackLocationInfo;Lnet/minecraft/server/packs/FilePackResources$SharedZipFileAccess;Ljava/lang/String;)V",
		at = @At("RETURN")
	)
	private void packforge$captureArchive(
		PackLocationInfo location,
		@Coerce Object zipFileAccess,
		String prefix,
		CallbackInfo ci
	) {
		if (zipFileAccess instanceof SharedZipFileAccessBridge bridge
			&& this instanceof FilePackResourcesArchiveHolder holder) {
			holder.packforge$setArchive(bridge);
		}
	}
}
