package com.teenkung.packforge.mixin.compat;

import com.teenkung.packforge.internal.compat.MixinCompatibility;
import com.teenkung.packforge.platform.EarlyModDiscovery;
import com.teenkung.packforge.platform.OptimizationCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Selects mc26 ownership before any overlapping PackForge mixin transforms its target. */
public class OptimizationMixinPlugin implements IMixinConfigPlugin {
	private OptimizationCompatibility.State compatibility;

	@Override
	public void onLoad(String mixinPackage) {
		this.compatibility = EarlyModDiscovery.capture();
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (this.compatibility == null) {
			throw new IllegalStateException("PackForge mixin ownership requested before loader discovery");
		}
		return MixinCompatibility.shouldApply(mixinClassName, this.compatibility);
	}

	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
