package com.teenkung.packforge.client.mixin.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Selects the GUI owner without loading Minecraft classes or using reflection. */
public final class GuiCompatMixinPlugin implements IMixinConfigPlugin {
	private static final String MINECRAFT_MIXIN = "com.teenkung.packforge.client.mixin.compat.MinecraftGui26_1Mixin";
	private static final String GUI_MIXIN = "com.teenkung.packforge.client.mixin.compat.Gui26_2Mixin";
	private static final String SET_SCREEN_DESCRIPTOR = "(Lnet/minecraft/client/gui/screens/Screen;)V";

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (MINECRAFT_MIXIN.equals(mixinClassName) || GUI_MIXIN.equals(mixinClassName)) {
			return hasMethod(targetClassName, "setScreen", SET_SCREEN_DESCRIPTOR);
		}
		return true;
	}

	private static boolean hasMethod(String targetClassName, String name, String descriptor) {
		try {
			ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
			return target.methods.stream().anyMatch(method -> name.equals(method.name) && descriptor.equals(method.desc));
		} catch (ClassNotFoundException | IOException exception) {
			throw new IllegalStateException("Could not inspect PackForge GUI mixin target " + targetClassName, exception);
		}
	}

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
