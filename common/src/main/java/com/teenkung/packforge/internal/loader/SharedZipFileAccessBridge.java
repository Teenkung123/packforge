package com.teenkung.packforge.internal.loader;

import com.teenkung.packforge.loader.PackArchiveState;

import java.io.File;
import java.util.zip.ZipFile;

/**
 * Internal view added to Minecraft's shared ZIP owner by Mixin.
 *
 * <p>This interface must remain outside every configured Mixin package. Mixin
 * reserves those packages for transformation classes and rejects direct class
 * loading from them at runtime.</p>
 */
public interface SharedZipFileAccessBridge {
	File packforge$archiveFile();

	ZipFile packforge$getOrCreateZipFile();

	PackArchiveState packforge$archiveState();
}
