package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MultiAccountClientMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void aero$readBackgroundInput(boolean tick, CallbackInfo callback) {
        MultiAccountWorker.pumpInput((MinecraftClient) (Object) this);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void aero$captureBackgroundFrame(boolean tick, CallbackInfo callback) {
        MultiAccountWorker.renderedFrame((MinecraftClient) (Object) this);
    }

    @Inject(method = "isWindowFocused", at = @At("HEAD"), cancellable = true)
    private void aero$allowHiddenInput(CallbackInfoReturnable<Boolean> callback) {
        if (MultiAccountWorker.receivingInput()) callback.setReturnValue(true);
    }
}
