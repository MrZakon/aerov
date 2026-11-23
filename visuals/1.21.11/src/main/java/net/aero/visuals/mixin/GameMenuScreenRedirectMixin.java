package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroPauseScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenRedirectMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void aero$openCustomPause(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.currentScreen instanceof GameMenuScreen) client.setScreen(new AeroPauseScreen());
        });
    }
}
