package net.aero.visuals.mixin;

import net.aero.visuals.module.impl.misc.NightVisionModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class NightVisionLightmapMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void aero$nightVision(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (state.needsUpdate) {
            state.nightVisionEffectIntensity = NightVisionModule.lightmapIntensity(state.nightVisionEffectIntensity);
        }
    }
}
