package com.teenkung.packforge.loader;

import java.io.IOException;
import java.io.InputStream;

/** Minecraft-independent equivalent of the loader-specific checked I/O supplier. */
@FunctionalInterface
public interface InputStreamSupplier {
	InputStream get() throws IOException;
}
