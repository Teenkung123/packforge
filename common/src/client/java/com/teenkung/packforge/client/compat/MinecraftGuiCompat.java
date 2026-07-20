package com.teenkung.packforge.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MinecraftGuiCompat {
	private static final Method MINECRAFT_SET_SCREEN = findMethod(Minecraft.class, "setScreen", Screen.class);
	private static final Method GUI_SET_SCREEN = findMethod(Gui.class, "setScreen", Screen.class);
	private static final Field MINECRAFT_SCREEN = findField(Minecraft.class, "screen");
	private static final Method GUI_SCREEN = findMethod(Gui.class, "screen");
	private static final Method MINECRAFT_SET_OVERLAY = findMethod(Minecraft.class, "setOverlay", Overlay.class);
	private static final Method GUI_SET_OVERLAY = findMethod(Gui.class, "setOverlay", Overlay.class);
	private static final Method MINECRAFT_TOAST_MANAGER = findMethod(Minecraft.class, "getToastManager");
	private static final Method GUI_TOAST_MANAGER = findMethod(Gui.class, "toastManager");

	public static void setScreen(Minecraft minecraft, Screen screen) {
		if (MINECRAFT_SET_SCREEN != null) {
			invoke(MINECRAFT_SET_SCREEN, minecraft, screen);
			return;
		}
		if (GUI_SET_SCREEN != null) {
			invoke(GUI_SET_SCREEN, minecraft.gui, screen);
			return;
		}
		throw new IllegalStateException("Could not find a Minecraft screen setter");
	}

	public static Screen screen(Minecraft minecraft) {
		if (MINECRAFT_SCREEN != null) {
			return get(MINECRAFT_SCREEN, minecraft);
		}
		if (GUI_SCREEN != null) {
			return invoke(GUI_SCREEN, minecraft.gui);
		}
		return null;
	}

	public static void setOverlay(Minecraft minecraft, Overlay overlay) {
		if (MINECRAFT_SET_OVERLAY != null) {
			invoke(MINECRAFT_SET_OVERLAY, minecraft, overlay);
			return;
		}
		if (GUI_SET_OVERLAY != null) {
			invoke(GUI_SET_OVERLAY, minecraft.gui, overlay);
			return;
		}
		throw new IllegalStateException("Could not find a Minecraft overlay setter");
	}

	public static ToastManager toastManager(Minecraft minecraft) {
		if (MINECRAFT_TOAST_MANAGER != null) {
			return invoke(MINECRAFT_TOAST_MANAGER, minecraft);
		}
		if (GUI_TOAST_MANAGER != null) {
			return invoke(GUI_TOAST_MANAGER, minecraft.gui);
		}
		throw new IllegalStateException("Could not find a Minecraft toast manager accessor");
	}

	private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
		try {
			return owner.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException ignored) {
			return null;
		}
	}

	private static Field findField(Class<?> owner, String name) {
		try {
			Field field = owner.getField(name);
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException ignored) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T get(Field field, Object target) {
		try {
			return (T) field.get(target);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Could not read " + field.getDeclaringClass().getName() + "#" + field.getName(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T invoke(Method method, Object target, Object... args) {
		try {
			return (T) method.invoke(target, args);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Could not call " + method.getDeclaringClass().getName() + "#" + method.getName(), e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Could not call " + method.getDeclaringClass().getName() + "#" + method.getName(), cause);
		}
	}

	private MinecraftGuiCompat() {}
}
