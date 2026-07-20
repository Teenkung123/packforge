package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.loader.PackArchiveState;
import com.teenkung.packforge.internal.loader.SharedZipFileAccessBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess")
public abstract class SharedZipFileAccessMixin implements SharedZipFileAccessBridge {
	@Unique
	private PackArchiveState packforge$state;

	@Inject(method = "<init>(Ljava/io/File;)V", at = @At("RETURN"))
	private void packforge$createState(File archiveFile, CallbackInfo ci) {
		this.packforge$state = new PackArchiveState();
	}

	@Override
	@Unique
	public File packforge$archiveFile() {
		return ((SharedZipFileAccessAccessor) (Object) this).packforge$file();
	}

	@Override
	@Unique
	public ZipFile packforge$getOrCreateZipFile() {
		return ((SharedZipFileAccessAccessor) (Object) this).packforge$invokeGetOrCreateZipFile();
	}

	@Override
	@Unique
	public PackArchiveState packforge$archiveState() {
		return this.packforge$state;
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void packforge$closeStateBeforeVanilla(CallbackInfo ci) {
		try {
			this.packforge$state.close();
		} catch (IOException exception) {
			PackForge.LOGGER.warn(
				"Failed to close PackForge ZIP state for {}; vanilla ZIP close will continue",
				this.packforge$archiveFile(),
				exception
			);
		}
	}
}
