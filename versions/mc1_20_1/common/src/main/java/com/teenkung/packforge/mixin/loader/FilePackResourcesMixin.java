package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.PackForge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.InputStreamSupplier;
import com.teenkung.packforge.loader.LoaderTimings;
import com.teenkung.packforge.loader.PackArchiveState;
import com.teenkung.packforge.loader.PackIndex;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ZipReadPool;
import net.minecraft.server.packs.FilePackResources;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
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
	private PackIndex packforge$index(ZipFile zipFile) {
		if (!this.packforge$loaderIndexEnabled() || zipFile == null) {
			return null;
		}
		PackArchiveState archiveState = this.packforge$archiveState;
		if (archiveState == null || archiveState.isClosed()) {
			return null;
		}
		try {
			if (this.packforge$callGetOrCreateZipFile() != zipFile) {
				return null;
			}
		} catch (RuntimeException ignored) {
			return null;
		}
		return archiveState.index(
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
		IoSupplier<InputStream> fallbackSupplier,
		boolean duplicatePath
	) {
		if (!this.packforge$loaderZipPoolEnabled() || duplicatePath) {
			return fallbackSupplier;
		}
		InputStreamSupplier fallback = fallbackSupplier::get;
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
		Operation<Enumeration<? extends ZipEntry>> original,
		@Local(argsOnly = true) PackType type
	) {
		PackIndex index = this.packforge$index(zipFile);
		if (index == null) {
			return original.call(zipFile);
		}
		LoaderTimings.recordGetNamespaces();
		return index.entriesWithPrefix(type.getDirectory() + "/");
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
		Operation<Enumeration<? extends ZipEntry>> original,
		@Local(argsOnly = true) PackType type,
		@Local(argsOnly = true, ordinal = 0) String namespace,
		@Local(argsOnly = true, ordinal = 1) String directory
	) {
		PackIndex index = this.packforge$index(zipFile);
		if (index == null) {
			return original.call(zipFile);
		}
		String root = type.getDirectory() + "/" + namespace + "/";
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
