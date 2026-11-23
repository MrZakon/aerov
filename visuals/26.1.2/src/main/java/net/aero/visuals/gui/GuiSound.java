package net.aero.visuals.gui;

import net.aero.visuals.AeroVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

final class GuiSound {
    private GuiSound() {}

    static void play(String id) {
        Minecraft client = Minecraft.getInstance();
        if (client.getSoundManager() != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID, id)), 1.0F));
        }
    }
}
