package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountView;
import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MultiAccountWorldRenderMixin {
    @Shadow public abstract void updateCrosshairTarget(float tickDelta);

    @Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
    private void aero$skipCoveredWorld(CallbackInfo callback) {
        if (MultiAccountView.active()) callback.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void aero$parkHiddenRender(RenderTickCounter ticks, boolean tick, CallbackInfo callback) {
        if (MultiAccountWorker.planFrame(MinecraftClient.getInstance())) {
            updateCrosshairTarget(ticks.getTickProgress(true));
            callback.cancel();
        }
    }
}
