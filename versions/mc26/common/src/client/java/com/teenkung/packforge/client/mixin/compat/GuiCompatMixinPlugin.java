package com.teenkung.packforge.client.mixin.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Selects the compatible 26.x GUI hook from the target class shape. */
public final class GuiCompatMixinPlugin implements IMixinConfigPlugin {
	private static final String MINECRAFT_MIXIN = "com.teenkung.packforge.client.mixin.compat.MinecraftGui26_1Mixin";
	private static final String GUI_MIXIN = "com.teenkung.packforge.client.mixin.compat.Gui26_2Mixin";
	private static final String SET_SCREEN_DESCRIPTOR = "(Lnet/minecraft/client/gui/screens/Screen;)V";

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.equals(MINECRAFT_MIXIN)
			&& hasMethod(targetClassName, "setScreen", SET_SCREEN_DESCRIPTOR)) {
			return false;
		}
		if (mixinClassName.equals(GUI_MIXIN)
			&& !hasMethod(targetClassName, "setScreen", SET_SCREEN_DESCRIPTOR)) {
			return false;
		}
		return true;
	}

	private static boolean hasMethod(String targetClassName, String name, String descriptor) {
		try {
			ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
			return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
		} catch (ClassNotFoundException | IOException exception) {
			return false;
		}
	}

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
