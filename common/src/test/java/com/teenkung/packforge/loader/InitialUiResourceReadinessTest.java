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
	void acceptsLegacyAliases() {
		InitialUiResourceReadiness readiness = new InitialUiResourceReadiness();
		readiness.listenerApplied("net.minecraft.client.renderer.ShaderManager Reload Listener");
		readiness.listenerApplied("font_loader");
		assertTrue(readiness.isReady());
	}
}
