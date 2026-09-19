package com.teenkung.packforge.client.atlas;

import net.minecraft.resources.Identifier;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Invocation identity explicitly carried across sprite preparation executor boundaries. */
public final class AtlasLoadInvocation {
	private static final ThreadLocal<AtlasLoadInvocation> CURRENT = new ThreadLocal<>();
	private final Identifier atlas;
	private final boolean resourcePackUnboundedOwner;
	private final AtlasDiagnostics diagnostics;
	private SpriteMetadataCache.AtlasState state;

	public AtlasLoadInvocation(Identifier atlas, boolean resourcePackUnboundedOwner) {
		this.atlas = atlas;
		this.resourcePackUnboundedOwner = resourcePackUnboundedOwner;
		this.diagnostics = AtlasDiagnostics.capture(atlas.toString());
	}

	public static AtlasLoadInvocation current() { return CURRENT.get(); }

	public AtlasDiagnostics diagnostics() { return diagnostics; }

	public <T> T call(Supplier<T> action) {
		AtlasLoadInvocation previous = CURRENT.get();
		CURRENT.set(this);
		try {
			return action.get();
		} finally {
			if (previous == null) CURRENT.remove();
			else CURRENT.set(previous);
		}
	}

	public Executor bind(Executor executor) {
		Executor observed = diagnostics == null ? executor : diagnostics.bind(executor);
		return command -> observed.execute(() -> call(() -> {
			command.run();
			return null;
		}));
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
