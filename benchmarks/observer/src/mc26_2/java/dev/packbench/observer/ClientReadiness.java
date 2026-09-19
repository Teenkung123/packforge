package dev.packbench.observer;

import dev.packbench.observer.mixin.TitleScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

final class ClientReadiness {
    static boolean ready(Minecraft minecraft) {
        return minecraft.gui.screen() instanceof TitleScreen title
            && !minecraft.getWindow().isMinimized() && !((TitleScreenAccessor) title).benchmark$isFading();
    }

    static boolean worldReady(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null && minecraft.gui.screen() == null
            && !minecraft.getWindow().isMinimized()
            && minecraft.isGameLoadFinished() && minecraft.mouseHandler.isMouseGrabbed();
    }

    static TitleScreen titleScreen(Minecraft minecraft) { return (TitleScreen) minecraft.gui.screen(); }
    static boolean overlayPresent(Minecraft minecraft) { return minecraft.gui.overlay() != null; }
    static boolean titleFading(Minecraft minecraft) {
        return minecraft.gui.screen() instanceof TitleScreen title && ((TitleScreenAccessor) title).benchmark$isFading();
    }

    private ClientReadiness() {}
}
