package com.teenkung.packforge.platform;

import java.util.Optional;

/** Loader-neutral access to optional-mod metadata after platform initialization. */
public interface OptionalModPresence {
	boolean isModLoaded(String modId);

	/** Returns a public loader metadata version when this loader exposes one. */
	default Optional<String> modVersion(String modId) {
		return Optional.empty();
	}
}
