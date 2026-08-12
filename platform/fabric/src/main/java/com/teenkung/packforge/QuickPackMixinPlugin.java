package com.teenkung.packforge;

import com.teenkung.packforge.compat.VersionedMixinGate;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Suppresses overlap-bearing PackForge mixins before Mixin applies them. */
public final class QuickPackMixinPlugin implements IMixinConfigPlugin {
	private static final String QUICK_PACK = "quick-pack";
	private static final Set<String> QUICK_PACK_OWNED_MIXINS = Set.of(
		"mixin.loader.FilePackResourcesMixin",
		"mixin.loader.SharedZipFileAccessAccessor",
		"mixin.loader.SharedZipFileAccessMixin",
		"client.mixin.font.FontManagerMixin",
		"client.mixin.font.FontSetMixin",
		"client.mixin.ui.LoadingOverlayMixin",
		"client.mixin.ui.LoadingOverlayToastMixin"
	);

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		FabricLoader loader = FabricLoader.getInstance();
		if (!VersionedMixinGate.shouldApply(mixinClassName, minecraftVersion(loader))) {
			return false;
		}
		return !loader.isModLoaded(QUICK_PACK)
			|| QUICK_PACK_OWNED_MIXINS.stream().noneMatch(mixinClassName::endsWith);
	}

	private static String minecraftVersion(FabricLoader loader) {
		return loader.getModContainer("minecraft")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
