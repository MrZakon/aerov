package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroPauseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenRedirectMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void aero$openCustomPause(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen instanceof PauseScreen) client.setScreen(new AeroPauseScreen());
        });
    }
}
