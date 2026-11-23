package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Передаёт GLFW-клавиши в шину событий до ванильной обработки. */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$onKey(long window, int action, KeyInput input, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getInactivityFpsLimiter() != null) {
            client.getInactivityFpsLimiter().onInput();
        }
        if (net.aero.visuals.alt.MultiAccountView.routeKey(input.key(), input.scancode(), input.modifiers(), action, input.key())) {
            callback.cancel();
            return;
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onKey(input.key(), input.scancode(), action, input.modifiers())) {
            callback.cancel();
        }
    }
}
