package com.teenkung.packforge.loader;

import java.util.Locale;

/** Minecraft-version-specific resource identifier validation without Minecraft classes. */
public interface ResourceNamePolicy {
	boolean isValidNamespace(String namespace);

	boolean isValidPath(String path);

	default boolean omitEmptyNamespaceSegments() {
		return false;
	}

	static ResourceNamePolicy current() {
		return Standard.CURRENT;
	}

	static ResourceNamePolicy legacy1201() {
		return Standard.LEGACY_1_20_1;
	}

	enum Standard implements ResourceNamePolicy {
		/** Namespace and path rules used by modern ResourceLocation/Identifier. */
		CURRENT {
			@Override
			public boolean isValidNamespace(String namespace) {
				return namespace != null && !namespace.isEmpty() && validNamespaceCharacters(namespace);
			}
		},

		/**
		 * Minecraft 1.20.1 FilePackResources only rejects namespaces whose
		 * Locale.ROOT lowercase form differs from the original value.
		 */
		LEGACY_1_20_1 {
			@Override
			public boolean isValidNamespace(String namespace) {
				return namespace != null
					&& !namespace.isEmpty()
					&& namespace.equals(namespace.toLowerCase(Locale.ROOT));
			}

			@Override
			public boolean omitEmptyNamespaceSegments() {
				return true;
			}
		};

		@Override
		public boolean isValidPath(String path) {
			if (path == null || path.isEmpty()) {
				return false;
			}
			for (int i = 0; i < path.length(); i++) {
				char character = path.charAt(i);
				if ((character >= 'a' && character <= 'z')
					|| (character >= '0' && character <= '9')
					|| character == '_'
					|| character == '-'
					|| character == '.'
					|| character == '/') {
					continue;
				}
				return false;
			}
			return true;
		}

		private static boolean validNamespaceCharacters(String namespace) {
			for (int i = 0; i < namespace.length(); i++) {
				char character = namespace.charAt(i);
				if ((character >= 'a' && character <= 'z')
					|| (character >= '0' && character <= '9')
					|| character == '_'
					|| character == '-'
					|| character == '.') {
					continue;
				}
				return false;
			}
			return true;
		}
	}
}
