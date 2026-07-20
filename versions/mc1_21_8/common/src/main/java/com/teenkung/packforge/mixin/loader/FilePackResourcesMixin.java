package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.InputStreamSupplier;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.PackIndex;
import com.teenkung.packforge.loader.ResourceNamePolicy;
import com.teenkung.packforge.internal.loader.SharedZipFileAccessBridge;
import com.teenkung.packforge.loader.ZipReadPool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
	@Shadow @Final private static Logger LOGGER = null;
	@Shadow @Final private String prefix;

	@Unique
	private SharedZipFileAccessBridge packforge$archive;

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
		this.packforge$archive = (SharedZipFileAccessBridge) zipFileAccess;
	}

	@Unique
	private String packforge$addPrefix(String path) {
		return this.prefix.isEmpty() ? path : this.prefix + "/" + path;
	}

	@Unique
	private PackIndex packforge$index() {
		if (!FeatureFlags.loaderIndexEnabled()) {
			return null;
		}
		SharedZipFileAccessBridge archive = this.packforge$archive;
		ZipFile zipFile = archive.packforge$getOrCreateZipFile();
		if (zipFile == null) {
			return null;
		}
		return archive.packforge$archiveState().index(
			zipFile,
			archive.packforge$archiveFile().toString(),
			failure -> LOGGER.warn(
				"PackIndex build failed for {}; using vanilla until reopen",
				failure.archiveName(),
				failure.cause()
			)
		);
	}

	@Unique
	private IoSupplier<InputStream> packforge$supplier(
		String path,
		ZipFile fallbackZip,
		ZipEntry fallbackEntry,
		boolean duplicatePath
	) {
		IoSupplier<InputStream> vanilla = IoSupplier.create(fallbackZip, fallbackEntry);
		if (!FeatureFlags.loaderZipPoolEnabled() || duplicatePath) {
			return vanilla;
		}
		InputStreamSupplier fallback = vanilla::get;
		InputStreamSupplier pooled = this.packforge$archive.packforge$archiveState().pooledSupplier(
			this.packforge$archive.packforge$archiveFile(),
			ZipReadPool.DEFAULT_MAX_HANDLES,
			path,
			fallback,
			failure -> LOGGER.warn(
				"PackForge ZIP read pool failed for {}; using vanilla reads until reopen",
				failure.archiveFile(),
				failure.cause()
			)
		);
		return pooled::get;
	}

	@Inject(
		method = "getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/IoSupplier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void packforge$fastGetResource(
		PackType type,
		ResourceLocation location,
		CallbackInfoReturnable<IoSupplier<InputStream>> cir
	) {
		PackIndex index = this.packforge$index();
		if (index == null) {
			return;
		}
		String fullPath = this.packforge$addPrefix(
			type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath()
		);
		ZipEntry entry = index.entryFor(fullPath);
		LoaderTimings.recordGetResource();
		cir.setReturnValue(entry == null ? null : this.packforge$supplier(
			fullPath,
			index.zipFile(),
			entry,
			index.hasDuplicatePath(fullPath)
		));
	}

	@Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
	private void packforge$fastGetNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
		PackIndex index = this.packforge$index();
		if (index == null) {
			return;
		}
		String typePrefix = this.packforge$addPrefix(type.getDirectory() + "/");
		PackIndex.NamespaceResult namespaces = index.namespacesFor(typePrefix, ResourceNamePolicy.current());
		for (String invalid : namespaces.invalid()) {
			LOGGER.warn(
				"Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring",
				invalid,
				this.packforge$archive.packforge$archiveFile()
			);
		}
		LoaderTimings.recordGetNamespaces();
		cir.setReturnValue(namespaces.valid());
	}

	@Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
	private void packforge$fastListResources(
		PackType type,
		String namespace,
		String directory,
		PackResources.ResourceOutput output,
		CallbackInfo ci
	) {
		PackIndex index = this.packforge$index();
		if (index == null) {
			return;
		}
		String root = this.packforge$addPrefix(type.getDirectory() + "/" + namespace + "/");
		String searchPrefix = root + directory + "/";
		LoaderTimings.recordListResources();
		index.forEachFileWithPrefix(searchPrefix, indexedEntry -> {
			String path = indexedEntry.path().substring(root.length());
			ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
			if (location == null) {
				LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, path);
				return;
			}
			output.accept(
				location,
				this.packforge$supplier(
					indexedEntry.path(),
					index.zipFile(),
					indexedEntry.zipEntry(),
					indexedEntry.duplicatePath()
				)
			);
		});
		ci.cancel();
	}
}
