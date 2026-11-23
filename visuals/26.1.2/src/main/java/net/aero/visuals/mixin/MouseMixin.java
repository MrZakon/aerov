package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.hud.HudDragController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Мышь и колесо публикуются как отменяемые client-only события. */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Inject(method = "onMove(JDD)V", at = @At("RETURN"))
    private void aero$hudPointerMoved(long window, double x, double y, CallbackInfo callback) {
        HudDragController.pointerMoved(window);
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || window != client.getWindow().handle()) return;
        if (client.getFramerateLimitTracker() != null) {
            client.getFramerateLimitTracker().onInputReceived();
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseButton(input.button(), action, input.modifiers())) callback.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || window != client.getWindow().handle()) return;
        if (client.getFramerateLimitTracker() != null) {
            client.getFramerateLimitTracker().onInputReceived();
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseScroll(horizontal, vertical)) callback.cancel();
    }
}
