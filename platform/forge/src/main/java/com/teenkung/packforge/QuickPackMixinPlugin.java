package com.teenkung.packforge;

import com.teenkung.packforge.compat.VersionedMixinGate;
import com.teenkung.packforge.forge.ForgeModListCompat;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.VersionInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Suppresses overlap-bearing PackForge mixins before Mixin applies them. */
public final class QuickPackMixinPlugin implements IMixinConfigPlugin {
	private static final String QUICK_PACK = "quick-pack";
	private static final String RELOAD_OBSERVER_MIXIN = "mixin.observe.ReloadableResourceManagerMixin";
	private static final String FORGE_LEGACY_RELOAD_OBSERVER_MIXIN =
		"mixin.observe.ForgeLegacyReloadableResourceManagerMixin";
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
		String minecraftVersion = minecraftVersion();
		if (!VersionedMixinGate.shouldApply(mixinClassName, minecraftVersion)) {
			return false;
		}
		if (isReloadObserverMixin(mixinClassName)
			&& !shouldApplyReloadObserver(mixinClassName, minecraftVersion)) {
			return false;
		}
		return !ForgeModListCompat.isLoaded(QUICK_PACK)
			|| QUICK_PACK_OWNED_MIXINS.stream().noneMatch(mixinClassName::endsWith);
	}

	static boolean shouldApplyReloadObserver(String mixinClassName, String minecraftVersion) {
		boolean usesForgeLegacyObserver = isForgeLegacyReloadVersion(minecraftVersion);
		if (mixinClassName.endsWith(FORGE_LEGACY_RELOAD_OBSERVER_MIXIN)) {
			return usesForgeLegacyObserver;
		}
		if (mixinClassName.endsWith(RELOAD_OBSERVER_MIXIN)) {
			return !usesForgeLegacyObserver;
		}
		return true;
	}

	private static boolean isReloadObserverMixin(String mixinClassName) {
		return mixinClassName.endsWith(RELOAD_OBSERVER_MIXIN)
			|| mixinClassName.endsWith(FORGE_LEGACY_RELOAD_OBSERVER_MIXIN);
	}

	private static boolean isForgeLegacyReloadVersion(String minecraftVersion) {
		return "1.20.3".equals(minecraftVersion) || "1.20.4".equals(minecraftVersion);
	}

	private static String minecraftVersion() {
		VersionInfo versionInfo = FMLLoader.versionInfo();
		return versionInfo == null ? "" : versionInfo.mcVersion();
	}

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
