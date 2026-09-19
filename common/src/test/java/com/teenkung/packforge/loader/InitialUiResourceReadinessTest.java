package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialUiResourceReadinessTest {
	@Test
	void waitsForBothShaderAndFontResources() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		assertFalse(readiness.isReady());
		readiness.listenerApplied("Shader Loader");
		assertFalse(readiness.isReady());
		readiness.listenerApplied("FontManager");
		assertTrue(readiness.isReady());
	}

	@Test
	void acceptsEitherListenerCompletionOrder() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		readiness.listenerApplied("FontManager");
		assertFalse(readiness.isReady());
		readiness.listenerApplied("Shader Loader");
		assertTrue(readiness.isReady());
	}

	@Test
	void ignoresUnrelatedListenerNames() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		readiness.listenerApplied(null);
		readiness.listenerApplied("");
		readiness.listenerApplied("TextureManager");
		assertFalse(readiness.isReady());
	}

	@Test
	void acceptsDecoratedListenerNames() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		readiness.listenerApplied("minecraft:shaders (Shader Loader)");
		readiness.listenerApplied("minecraft:fonts (FontManager)");

		assertTrue(readiness.isReady());
	}

	@Test
	void resourceKeysAloneDoNotProveReadiness() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		readiness.listenerApplied("minecraft:shaders");
		assertFalse(readiness.isReady());
		readiness.listenerApplied("minecraft:fonts");
		readiness.listenerApplied("minecraft:shadermanager");
		readiness.listenerApplied("minecraft:fontmanager");

		assertFalse(readiness.isReady());
	}

	@Test
	void acceptsIntermediaryVanillaListenerNames() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		readiness.listenerApplied("class_757");
		assertFalse(readiness.isReady());
		readiness.listenerApplied("class_378");

		assertTrue(readiness.isReady());
	}

	@Test
	void acceptsModernIntermediaryShaderManagerName() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		readiness.listenerApplied("class_10151");
		assertFalse(readiness.isReady());
		readiness.listenerApplied("class_378");

		assertTrue(readiness.isReady());
	}

	@Test
	void ignoresIntermediaryClassPrefixCollisions() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();

		readiness.listenerApplied("class_7578");
		readiness.listenerApplied("class_3780");

		assertFalse(readiness.isReady());
	}

	@Test
	void acceptsLegacyAliases() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();
		readiness.listenerApplied("net.minecraft.client.renderer.ShaderManager Reload Listener");
		readiness.listenerApplied("font_loader");
		assertTrue(readiness.isReady());
	}
}
