package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.internal.loader.FilePackResourcesArchiveHolder;
import com.teenkung.packforge.internal.loader.SharedZipFileAccessBridge;
import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesArchiveMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void packforge$captureArchive(
		String name,
		@Coerce Object zipFileAccess,
		boolean closeOnExit,
		String prefix,
		CallbackInfo ci
	) {
		if (zipFileAccess instanceof SharedZipFileAccessBridge bridge
			&& this instanceof FilePackResourcesArchiveHolder holder) {
			holder.packforge$setArchive(bridge);
		}
	}
}
