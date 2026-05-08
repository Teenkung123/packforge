package com.teenkung.packforge.mixin.loader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.File;
import java.util.zip.ZipFile;

@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess")
public interface SharedZipFileAccessAccessor {
	@Accessor("file")
	File packforge$file();

	@Invoker("getOrCreateZipFile")
	ZipFile packforge$getOrCreateZipFile();
}
