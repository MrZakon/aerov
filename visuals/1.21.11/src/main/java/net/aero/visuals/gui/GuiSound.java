package net.aero.visuals.gui;

import net.aero.visuals.AeroVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

final class GuiSound {
    private GuiSound() {}

    static void play(String id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(
                    SoundEvent.of(Identifier.of(AeroVisuals.MOD_ID, id)), 1.0F));
        }
    }
}
