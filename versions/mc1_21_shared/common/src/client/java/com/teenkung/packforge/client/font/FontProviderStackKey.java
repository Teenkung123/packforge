package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stable key for one resolved provider stack.
 *
 * <p>Provider and filter equality is deliberately identity based.  A font
 * stack can only share a prepared result when it has the same ordered
 * providers and the same filter objects; provider implementations are not
 * required to implement value equality and filter semantics may be mutable or
 * stateful.</p>
 */
public final class FontProviderStackKey {
	private final List<Slot> slots;
	private final int hash;

	private FontProviderStackKey(List<Slot> slots) {
		this.slots = List.copyOf(slots);
		this.hash = this.slots.hashCode();
	}

	public static FontProviderStackKey of(List<GlyphProvider.Conditional> providers) {
		Objects.requireNonNull(providers, "providers");
		List<Slot> slots = new ArrayList<>(providers.size());
		for (GlyphProvider.Conditional conditional : providers) {
			Objects.requireNonNull(conditional, "providers contains null");
			slots.add(new Slot(conditional.provider(), conditional.filter()));
		}
		return new FontProviderStackKey(slots);
	}

	public int size() {
		return this.slots.size();
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof FontProviderStackKey key && this.slots.equals(key.slots);
	}

	@Override
	public int hashCode() {
		return this.hash;
	}

	private record Slot(IdentityRef provider, IdentityRef filter) {
		private Slot(Object provider, Object filter) {
			this(new IdentityRef(provider), new IdentityRef(filter));
		}
	}

	private static final class IdentityRef {
		private final Object value;
		private final int hash;

		private IdentityRef(Object value) {
			this.value = Objects.requireNonNull(value, "identity value");
			this.hash = System.identityHashCode(value);
		}

		@Override
		public boolean equals(Object other) {
			return this == other || other instanceof IdentityRef ref && this.value == ref.value;
		}

		@Override
		public int hashCode() {
			return this.hash;
		}
	}
}
