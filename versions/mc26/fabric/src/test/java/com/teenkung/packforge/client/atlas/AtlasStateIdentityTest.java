package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasStateIdentityTest {
	private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks");
	private static final BoundedSpriteDecode.Plan PLAN = new BoundedSpriteDecode.Plan(
		false,
		false,
		false,
		256,
		true,
		2,
		1,
		1,
		Set.of()
	);

	@AfterEach
	void cleanup() {
		SpriteMetadataCache.resetForReload();
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context != null) {
			ReloadExecutionContext.finish(context);
		}
	}

	@Test
	void staleFailureCanOnlyReleaseItsCapturedAtlasState() {
		ReloadExecutionContext older = ReloadExecutionContext.startForTesting(ReloadFeatureSnapshot.capture());
		SpriteMetadataCache.AtlasState olderState = SpriteMetadataCache.bind(ATLAS, PLAN);

		ReloadExecutionContext newer = ReloadExecutionContext.startForTesting(ReloadFeatureSnapshot.capture());
		SpriteMetadataCache.AtlasState newerState = SpriteMetadataCache.bind(ATLAS, PLAN);
		assertNotSame(olderState, newerState);

		SpriteMetadataCache.fail(olderState, null);

		assertFalse(SpriteMetadataCache.contains(olderState));
		assertTrue(SpriteMetadataCache.contains(newerState));
		SpriteMetadataCache.fail(newerState, null);
		assertFalse(SpriteMetadataCache.contains(newerState));
		assertFalse(ReloadExecutionContext.finish(older));
		ReloadExecutionContext.finish(newer);
	}
}
