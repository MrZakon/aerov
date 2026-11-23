package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountPointer;
import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MultiAccountMouseMixin implements MultiAccountPointer {
    @Shadow private boolean mouseGrabbed;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Invoker("onMove") protected abstract void aero$invokeMove(long window, double x, double y);

    @Override public void aero$point(double x, double y) {
        var window = Minecraft.getInstance().getWindow();
        aero$invokeMove(window.handle(), x * window.getScreenWidth(), y * window.getScreenHeight());
    }

    @Override public void aero$resetMotion() {
        accumulatedDX = accumulatedDY = 0;
    }

    @Override public void aero$look(double x, double y) {
        if (!mouseGrabbed) return;
        accumulatedDX += x;
        accumulatedDY += y;
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void aero$grabHidden(CallbackInfo callback) {
        if (!MultiAccountWorker.requested()) return;
        mouseGrabbed = true;
        callback.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void aero$releaseHidden(CallbackInfo callback) {
        if (!MultiAccountWorker.requested()) return;
        mouseGrabbed = false;
        aero$resetMotion();
        callback.cancel();
    }
}
