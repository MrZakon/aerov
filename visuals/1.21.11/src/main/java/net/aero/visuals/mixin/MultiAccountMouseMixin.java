package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountPointer;
import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MultiAccountMouseMixin implements MultiAccountPointer {
    @Shadow private boolean cursorLocked;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;
    @Invoker("onCursorPos") protected abstract void aero$invokeMove(long window, double x, double y);

    @Override public void aero$point(double x, double y) {
        var window = MinecraftClient.getInstance().getWindow();
        aero$invokeMove(window.getHandle(), x * window.getWidth(), y * window.getHeight());
    }

    @Override public void aero$resetMotion() {
        cursorDeltaX = cursorDeltaY = 0;
    }

    @Override public void aero$look(double x, double y) {
        if (!cursorLocked) return;
        cursorDeltaX += x;
        cursorDeltaY += y;
    }

    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void aero$grabHidden(CallbackInfo callback) {
        if (!MultiAccountWorker.requested()) return;
        cursorLocked = true;
        callback.cancel();
    }

    @Inject(method = "unlockCursor", at = @At("HEAD"), cancellable = true)
    private void aero$releaseHidden(CallbackInfo callback) {
        if (!MultiAccountWorker.requested()) return;
        cursorLocked = false;
        aero$resetMotion();
        callback.cancel();
    }
}
