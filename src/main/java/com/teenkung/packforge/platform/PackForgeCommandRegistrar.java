package com.teenkung.packforge.platform;

public interface PackForgeCommandRegistrar {
	void register(PackForgeCommandBridge bridge);

	interface PackForgeCommandBridge {
		void registerLiteral(String name, Runnable action);
	}
}
