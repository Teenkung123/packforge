package dev.packbench.observer;

import dev.packbench.observer.mixin.TitleScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

final class ClientReadiness {
    static boolean ready(Minecraft minecraft) {
        return minecraft.screen instanceof TitleScreen title
            && !minecraft.getWindow().isMinimized() && !((TitleScreenAccessor) title).benchmark$isFading();
    }

    static boolean worldReady(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null && minecraft.screen == null
            && !minecraft.getWindow().isMinimized()
            && minecraft.isGameLoadFinished() && minecraft.mouseHandler.isMouseGrabbed();
    }

    static TitleScreen titleScreen(Minecraft minecraft) { return (TitleScreen) minecraft.screen; }
    static boolean overlayPresent(Minecraft minecraft) { return minecraft.getOverlay() != null; }
    static boolean titleFading(Minecraft minecraft) {
        return minecraft.screen instanceof TitleScreen title && ((TitleScreenAccessor) title).benchmark$isFading();
    }

    private ClientReadiness() {}
}
