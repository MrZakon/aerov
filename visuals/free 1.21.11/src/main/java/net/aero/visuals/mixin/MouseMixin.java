package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.hud.HudDragController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Мышь и колесо публикуются как отменяемые client-only события. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onCursorPos(JDD)V", at = @At("RETURN"))
    private void aero$hudPointerMoved(long window, double x, double y, CallbackInfo callback) {
        HudDragController.pointerMoved(window);
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$onMouseButton(long window, MouseInput input, int action, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getInactivityFpsLimiter() != null) {
            client.getInactivityFpsLimiter().onInput();
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseButton(input.button(), action, input.modifiers())) callback.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getInactivityFpsLimiter() != null) {
            client.getInactivityFpsLimiter().onInput();
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseScroll(horizontal, vertical)) callback.cancel();
    }
}
