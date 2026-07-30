package com.teenkung.packforge.client.mixin.ui;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LoadingOverlay.class) public interface LoadingOverlayAccessor { @Accessor("fadeOutStart") void packforge$setFadeOutStart(long value); }
