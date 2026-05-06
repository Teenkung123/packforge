package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.PackIndex;
import com.teenkung.packforge.loader.PackIndexCache;
import com.teenkung.packforge.loader.ZipFilePools;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
	@Shadow @Final private FilePackResources.SharedZipFileAccess zipFileAccess;
	@Shadow @Final private String prefix;

	@Unique
	private PackIndex packforge$index() {
		if (!FeatureFlags.loaderIndexEnabled()) return null;
		return PackIndexCache.getOrBuild(this.zipFileAccess);
	}

	@Unique
	private String packforge$addPrefix(String path) {
		return this.prefix.isEmpty() ? path : this.prefix + "/" + path;
	}

	@Inject(
		method = "getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/server/packs/resources/IoSupplier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void packforge$fastGetResource(PackType type, Identifier id, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
		PackIndex idx = packforge$index();
		if (idx == null) return;
		String full = packforge$addPrefix(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
		ZipEntry entry = idx.entryFor(full);
		LoaderTimings.recordGetResource();
		cir.setReturnValue(entry == null ? null : ZipFilePools.supplier(this.zipFileAccess, full, idx.zipFile(), entry));
	}

	@Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
	private void packforge$fastGetNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
		PackIndex idx = packforge$index();
		if (idx == null) return;
		String typePrefix = packforge$addPrefix(type.getDirectory() + "/");
		LoaderTimings.recordGetNamespaces();
		cir.setReturnValue(idx.namespacesFor(typePrefix));
	}

	@Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
	private void packforge$fastListResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output, CallbackInfo ci) {
		PackIndex idx = packforge$index();
		if (idx == null) return;
		String root = packforge$addPrefix(type.getDirectory() + "/" + namespace + "/");
		String prefix = root + directory + "/";
		LoaderTimings.recordListResources();
		idx.forEachWithPrefix(prefix, (name, entry) -> {
			String path = name.substring(root.length());
			Identifier built = Identifier.tryBuild(namespace, path);
			if (built != null) output.accept(built, ZipFilePools.supplier(this.zipFileAccess, name, idx.zipFile(), entry));
		});
		ci.cancel();
	}
}
