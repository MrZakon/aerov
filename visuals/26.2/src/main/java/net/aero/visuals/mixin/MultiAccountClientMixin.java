package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MultiAccountClientMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void aero$readBackgroundInput(boolean tick, CallbackInfo callback) {
        MultiAccountWorker.pumpInput((Minecraft) (Object) this);
    }

    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void aero$captureBackgroundFrame(boolean tick, CallbackInfo callback) {
        MultiAccountWorker.renderedFrame((Minecraft) (Object) this);
    }

    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void aero$allowHiddenInput(CallbackInfoReturnable<Boolean> callback) {
        if (MultiAccountWorker.receivingInput()) callback.setReturnValue(true);
    }
}
