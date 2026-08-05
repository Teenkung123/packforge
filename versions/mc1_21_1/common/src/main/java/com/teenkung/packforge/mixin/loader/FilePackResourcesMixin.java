package com.teenkung.packforge.mixin.loader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.internal.loader.SharedZipFileAccessBridge;
import com.teenkung.packforge.loader.InputStreamSupplier;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.PackArchiveState;
import com.teenkung.packforge.loader.PackIndex;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ZipReadPool;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
	@Shadow @Final private String prefix;

	@Unique
	private SharedZipFileAccessBridge packforge$archive;

	@WrapOperation(
		method = "getResource(Ljava/lang/String;)Lnet/minecraft/server/packs/resources/IoSupplier;",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/zip/ZipFile;getEntry(Ljava/lang/String;)Ljava/util/zip/ZipEntry;"
		)
	)
	private ZipEntry packforge$indexedGetEntry(
		ZipFile zipFile,
		String path,
		Operation<ZipEntry> original
	) {
		PackIndex index = this.packforge$index(zipFile);
		if (index == null) {
			return original.call(zipFile, path);
		}
		LoaderTimings.recordGetResource();
		return index.entryFor(path);
	}

	@WrapOperation(
		method = "getResource(Ljava/lang/String;)Lnet/minecraft/server/packs/resources/IoSupplier;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/IoSupplier;create(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipEntry;)Lnet/minecraft/server/packs/resources/IoSupplier;"
		)
	)
	private IoSupplier<InputStream> packforge$resourceSupplier(
		ZipFile zipFile,
		ZipEntry entry,
		Operation<IoSupplier<InputStream>> original
	) {
		return this.packforge$maybePooledSupplier(zipFile, entry, original);
	}

	@WrapOperation(
		method = "getNamespaces(Lnet/minecraft/server/packs/PackType;)Ljava/util/Set;",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;"
		)
	)
	private Enumeration<? extends ZipEntry> packforge$indexedNamespaceEntries(
		ZipFile zipFile,
		@Local(argsOnly = true) PackType type,
		Operation<Enumeration<? extends ZipEntry>> original
	) {
		PackIndex index = this.packforge$index(zipFile);
		if (index == null) {
			return original.call(zipFile);
		}
		LoaderTimings.recordGetNamespaces();
		return index.entriesWithPrefix(this.packforge$addPrefix(type.getDirectory() + "/"));
	}

	@WrapOperation(
		method = "listResources(Lnet/minecraft/server/packs/PackType;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;"
		)
	)
	private Enumeration<? extends ZipEntry> packforge$indexedResourceEntries(
		ZipFile zipFile,
		@Local(argsOnly = true) PackType type,
		@Local(argsOnly = true, ordinal = 0) String namespace,
		@Local(argsOnly = true, ordinal = 1) String directory,
		Operation<Enumeration<? extends ZipEntry>> original
	) {
		PackIndex index = this.packforge$index(zipFile);
		if (index == null) {
			return original.call(zipFile);
		}
		String root = this.packforge$addPrefix(type.getDirectory() + "/" + namespace + "/");
		LoaderTimings.recordListResources();
		return index.entriesWithPrefix(root + directory + "/");
	}

	@WrapOperation(
		method = "listResources(Lnet/minecraft/server/packs/PackType;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/IoSupplier;create(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipEntry;)Lnet/minecraft/server/packs/resources/IoSupplier;"
		)
	)
	private IoSupplier<InputStream> packforge$listedResourceSupplier(
		ZipFile zipFile,
		ZipEntry entry,
		Operation<IoSupplier<InputStream>> original
	) {
		return this.packforge$maybePooledSupplier(zipFile, entry, original);
	}

	@Inject(
		method = "<init>(Lnet/minecraft/server/packs/PackLocationInfo;Lnet/minecraft/server/packs/FilePackResources$SharedZipFileAccess;Ljava/lang/String;)V",
		at = @At("RETURN")
	)
	private void packforge$captureArchive(
		PackLocationInfo location,
		@Coerce Object zipFileAccess,
		String prefix,
		org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
	) {
		if (zipFileAccess instanceof SharedZipFileAccessBridge bridge) {
			this.packforge$archive = bridge;
		}
	}

	@Unique
	private String packforge$addPrefix(String path) {
		return this.prefix.isEmpty() ? path : this.prefix + "/" + path;
	}

	@Unique
	private PackIndex packforge$index(ZipFile zipFile) {
		if (!this.packforge$loaderIndexEnabled() || zipFile == null) {
			return null;
		}
		SharedZipFileAccessBridge archive = this.packforge$archive;
		if (archive == null) {
			return null;
		}
		PackArchiveState archiveState = archive.packforge$archiveState();
		if (archiveState == null || archiveState.isClosed()) {
			return null;
		}
		try {
			if (archive.packforge$getOrCreateZipFile() != zipFile) {
				return null;
			}
		} catch (RuntimeException ignored) {
			return null;
		}
		return archiveState.index(
			zipFile,
			archive.packforge$archiveFile().toString(),
			failure -> PackForge.LOGGER.warn(
				"PackIndex build failed for {}; using vanilla until reopen",
				failure.archiveName(),
				failure.cause()
			)
		);
	}

	@Unique
	private IoSupplier<InputStream> packforge$supplier(
		String path,
		IoSupplier<InputStream> fallbackSupplier,
		boolean duplicatePath
	) {
		if (!this.packforge$loaderZipPoolEnabled() || duplicatePath) {
			return fallbackSupplier;
		}
		InputStreamSupplier fallback = fallbackSupplier::get;
		InputStreamSupplier pooled = this.packforge$archive.packforge$archiveState().pooledSupplier(
			this.packforge$archive.packforge$archiveFile(),
			ZipReadPool.DEFAULT_MAX_HANDLES,
			path,
			fallback,
			failure -> PackForge.LOGGER.warn(
				"PackForge ZIP read pool failed for {}; using vanilla reads until reopen",
				failure.archiveFile(),
				failure.cause()
			)
		);
		return pooled::get;
	}

	@Unique
	private IoSupplier<InputStream> packforge$maybePooledSupplier(
		ZipFile zipFile,
		ZipEntry entry,
		Operation<IoSupplier<InputStream>> original
	) {
		IoSupplier<InputStream> vanilla = original.call(zipFile, entry);
		if (!this.packforge$loaderZipPoolEnabled()) {
			return vanilla;
		}
		PackIndex index = this.packforge$index(zipFile);
		if (index == null) {
			return vanilla;
		}
		String path = entry.getName();
		return this.packforge$supplier(path, vanilla, index.hasDuplicatePath(path));
	}

	@Unique
	private boolean packforge$loaderIndexEnabled() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null
			? FeatureFlags.loaderIndexEnabled()
			: context.features().loaderIndexEnabled();
	}

	@Unique
	private boolean packforge$loaderZipPoolEnabled() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null
			? FeatureFlags.loaderZipPoolEnabled()
			: context.features().loaderZipPoolEnabled();
	}
}
