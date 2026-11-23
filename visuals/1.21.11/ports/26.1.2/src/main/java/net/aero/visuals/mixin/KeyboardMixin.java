package net.aero.visuals.mixin;

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
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onKey(input.key(), input.scancode(), action, 0)) {
            callback.cancel();
        }
    }
}
