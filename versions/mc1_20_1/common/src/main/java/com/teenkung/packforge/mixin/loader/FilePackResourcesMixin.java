package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.InputStreamSupplier;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.PackArchiveState;
import com.teenkung.packforge.loader.PackIndex;
import com.teenkung.packforge.loader.ResourceNamePolicy;
import com.teenkung.packforge.loader.ZipReadPool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Minecraft 1.20.1 owns its ZipFile directly on FilePackResources. There is
 * no SharedZipFileAccess or CompositePackResources lifecycle in this target.
 */
@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
	@Shadow @Final private File file;

	@Unique
	private PackArchiveState packforge$archiveState;

	@Invoker("getOrCreateZipFile")
	protected abstract ZipFile packforge$callGetOrCreateZipFile();

	@Inject(
		method = "<init>(Ljava/lang/String;Ljava/io/File;Z)V",
		at = @At("RETURN")
	)
	private void packforge$createArchiveState(
		String packId,
		File archiveFile,
		boolean builtin,
		CallbackInfo ci
	) {
		this.packforge$archiveState = new PackArchiveState();
	}

	@Unique
	private PackIndex packforge$index() {
		if (!FeatureFlags.loaderIndexEnabled()) {
			return null;
		}
		ZipFile zipFile = this.packforge$callGetOrCreateZipFile();
		if (zipFile == null) {
			return null;
		}
		return this.packforge$archiveState.index(
			zipFile,
			this.file.toString(),
			failure -> PackForge.LOGGER.warn(
				"PackIndex build failed for {}; using vanilla until pack close",
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
		InputStreamSupplier pooled = this.packforge$archiveState.pooledSupplier(
			this.file,
			ZipReadPool.DEFAULT_MAX_HANDLES,
			path,
			fallback,
			failure -> PackForge.LOGGER.warn(
				"PackForge ZIP read pool failed for {}; using vanilla reads until pack close",
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
		String fullPath = type.getDirectory()
			+ "/" + location.getNamespace()
			+ "/" + location.getPath();
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
		String typePrefix = type.getDirectory() + "/";
		PackIndex.NamespaceResult namespaces = index.namespacesFor(
			typePrefix,
			ResourceNamePolicy.legacy1201()
		);
		for (String invalid : namespaces.invalid()) {
			PackForge.LOGGER.warn("Ignored non-lowercase namespace: {} in {}", invalid, this.file);
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
		String root = type.getDirectory() + "/" + namespace + "/";
		String searchPrefix = root + directory + "/";
		LoaderTimings.recordListResources();
		index.forEachFileWithPrefix(searchPrefix, indexedEntry -> {
			String path = indexedEntry.path().substring(root.length());
			ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
			if (location == null) {
				PackForge.LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, path);
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

	@Inject(method = "close", at = @At("HEAD"))
	private void packforge$closeStateBeforeVanilla(CallbackInfo ci) {
		PackArchiveState archiveState = this.packforge$archiveState;
		if (archiveState == null) {
			return;
		}
		try {
			archiveState.close();
		} catch (IOException exception) {
			PackForge.LOGGER.warn(
				"Failed to close PackForge ZIP state for {}; vanilla ZIP close will continue",
				this.file,
				exception
			);
		}
	}
}
