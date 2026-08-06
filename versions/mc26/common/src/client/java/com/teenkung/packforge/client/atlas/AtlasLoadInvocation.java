package com.teenkung.packforge.client.atlas;

import net.minecraft.resources.Identifier;

/** Per-thread state carried between the narrow sprite-loader mixin hooks. */
public final class AtlasLoadInvocation {
	private final Identifier atlas;
	private final boolean resourcePackUnboundedOwner;
	private SpriteMetadataCache.AtlasState state;

	public AtlasLoadInvocation(Identifier atlas, boolean resourcePackUnboundedOwner) {
		this.atlas = atlas;
		this.resourcePackUnboundedOwner = resourcePackUnboundedOwner;
	}

	public Identifier atlas() {
		return this.atlas;
	}

	public boolean resourcePackUnboundedOwner() {
		return this.resourcePackUnboundedOwner;
	}

	public SpriteMetadataCache.AtlasState state() {
		return this.state;
	}

	public void bindState(SpriteMetadataCache.AtlasState state) {
		this.state = state;
	}
}
