package net.aero.visuals.mixin;
import client.util.LegacyInput;

import net.aero.visuals.ClientRuntime;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Передаёт GLFW-клавиши в шину событий до ванильной обработки. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$onKey(long window, int action, KeyEvent input, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getFramerateLimitTracker() != null) {
            client.getFramerateLimitTracker().onInputReceived();
        }
        if (net.aero.visuals.alt.MultiAccountView.routeKey(input.key(), input.shortcutKey(), input.modifiers(), action, LegacyInput.key(input))) {
            callback.cancel();
            return;
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onKey(LegacyInput.key(input), input.key(), action, LegacyInput.modifiers(input.modifiers()))) {
            callback.cancel();
        }
    }
}
