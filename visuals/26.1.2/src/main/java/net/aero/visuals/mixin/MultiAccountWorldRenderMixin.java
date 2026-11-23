package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountView;
import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MultiAccountWorldRenderMixin {
    @Unique private boolean aero$worldCovered;

    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean aero$skipCoveredExtraction(boolean renderWorld) {
        // World render consumes and resets the extracted state. Skip both stages together.
        aero$worldCovered = MultiAccountView.active();
        return renderWorld && !aero$worldCovered;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean aero$skipCoveredRender(boolean renderWorld) {
        return renderWorld && !aero$worldCovered;
    }

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void aero$parkHiddenExtraction(CallbackInfo callback) {
        if (MultiAccountWorker.planFrame(Minecraft.getInstance())) callback.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void aero$parkHiddenRender(CallbackInfo callback) {
        if (MultiAccountWorker.skipPlannedFrame()) callback.cancel();
    }
}
