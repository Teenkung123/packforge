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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
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
		if (zipFileAccess instanceof SharedZipFileAccessBridge bridge) {
			this.packforge$archive = bridge;
		}
	}

	@Unique
	private String packforge$addPrefix(String path) {
		return this.prefix.isEmpty() ? path : this.prefix + "/" + path;
	}

	@Unique
	private boolean packforge$loaderIndexEnabled() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null ? FeatureFlags.loaderIndexEnabled() : context.features().loaderIndexEnabled();
	}

	@Unique
	private boolean packforge$loaderZipPoolEnabled() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null ? FeatureFlags.loaderZipPoolEnabled() : context.features().loaderZipPoolEnabled();
	}

	@Unique
	private PackIndex packforge$index(ZipFile expectedZip) {
		if (!this.packforge$loaderIndexEnabled()) {
			return null;
		}
		SharedZipFileAccessBridge archive = this.packforge$archive;
		if (archive == null) {
			return null;
		}
		PackArchiveState state = archive.packforge$archiveState();
		if (state == null || state.isClosed()) {
			return null;
		}
		try {
			if (archive.packforge$getOrCreateZipFile() != expectedZip) {
				return null;
			}
			expectedZip.size();
			PackIndex index = state.index(
				expectedZip,
				archive.packforge$archiveFile().toString(),
				failure -> PackForge.LOGGER.warn(
					"PackIndex build failed for {}; using vanilla until reopen",
					failure.archiveName(),
					failure.cause()
				)
			);
			return index == null || state.isClosed() || index.zipFile() != expectedZip ? null : index;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Unique
	private IoSupplier<InputStream> packforge$poolIfSafe(
		ZipFile zipFile,
		ZipEntry entry,
		IoSupplier<InputStream> vanilla
	) {
		if (!this.packforge$loaderZipPoolEnabled()) {
			return vanilla;
		}
		PackIndex index = this.packforge$index(zipFile);
		if (index == null || index.hasDuplicatePath(entry.getName())) {
			return vanilla;
		}
		SharedZipFileAccessBridge archive = this.packforge$archive;
		if (archive == null) {
			return vanilla;
		}
		InputStreamSupplier pooled = archive.packforge$archiveState().pooledSupplier(
			archive.packforge$archiveFile(),
			ZipReadPool.DEFAULT_MAX_HANDLES,
			entry.getName(),
			vanilla::get,
			failure -> PackForge.LOGGER.warn(
				"PackForge ZIP read pool failed for {}; using vanilla reads until reopen",
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
	private ZipEntry packforge$lookupEntry(
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
		IoSupplier<InputStream> vanilla = original.call(zipFile, entry);
		return this.packforge$poolIfSafe(zipFile, entry, vanilla);
	}

	@WrapOperation(
		method = "getNamespaces(Lnet/minecraft/server/packs/PackType;)Ljava/util/Set;",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;"
		)
	)
	private Enumeration<? extends ZipEntry> packforge$namespaceEntries(
		ZipFile zipFile,
		Operation<Enumeration<? extends ZipEntry>> original,
		@Local(argsOnly = true) PackType type
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
	private Enumeration<? extends ZipEntry> packforge$listEntries(
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
	private IoSupplier<InputStream> packforge$listSupplier(
		ZipFile zipFile,
		ZipEntry entry,
		Operation<IoSupplier<InputStream>> original
	) {
		IoSupplier<InputStream> vanilla = original.call(zipFile, entry);
		return this.packforge$poolIfSafe(zipFile, entry, vanilla);
	}
}
