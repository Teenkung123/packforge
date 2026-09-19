package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.concurrent.PreparationBudget;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.client.textures.SpriteContentsConstructor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticSpriteResourceLoaderTest {
	@Test
	void preservesResourceAndCallerSuppliedConstructorIncludingFailureAndClosedBypass() {
		PreparationBudget budget = new PreparationBudget(4096);
		PreparationBudget.Scope scope = budget.openScope();
		AtlasDiagnostics diagnostics = new AtlasDiagnostics(scope, scope.tryReserve(4096), 1, "test:atlas");
		Identifier id = Identifier.fromNamespaceAndPath("test", "sprite");
		Resource resource = new Resource(null, () -> null);
		SpriteContentsConstructor constructor = (sprite, size, image, animation, metadata, texture) -> null;
		AtomicInteger calls = new AtomicInteger();
		IllegalStateException failure = new IllegalStateException("Delegate failed");
		SpriteResourceLoader delegate = (actualId, actualResource, actualConstructor) -> {
			assertSame(id, actualId);
			assertSame(resource, actualResource);
			assertSame(constructor, actualConstructor);
			if (calls.incrementAndGet() == 2) throw failure;
			return null;
		};
		SpriteResourceLoader observed = diagnostics.wrap(delegate);
		assertNull(observed.loadSprite(id, resource, constructor));
		assertSame(failure, assertThrows(IllegalStateException.class, () -> observed.loadSprite(id, resource, constructor)));
		diagnostics.close();
		assertNull(observed.loadSprite(id, resource, constructor));
		assertEquals(3, calls.get());
		assertEquals(0, budget.used());
	}
}
