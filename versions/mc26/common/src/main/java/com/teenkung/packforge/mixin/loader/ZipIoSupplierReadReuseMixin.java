package com.teenkung.packforge.mixin.loader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ZipResourceReadReuse;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Wraps the stream call, leaving vanilla's persistent supplier object unchanged. */
@Mixin(IoSupplier.class)
public interface ZipIoSupplierReadReuseMixin {
	@WrapOperation(
		method = "lambda$create$1(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;",
		at = @At(value = "INVOKE", target = "Ljava/util/zip/ZipFile;getInputStream(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;")
	)
	private static InputStream packforge$reuseCertifiedRead(ZipFile zipFile, ZipEntry entry,
		Operation<InputStream> original) throws IOException {
		return ZipResourceReadReuse.open(ReloadExecutionContext.current(), zipFile, entry,
			() -> original.call(zipFile, entry));
	}
}
