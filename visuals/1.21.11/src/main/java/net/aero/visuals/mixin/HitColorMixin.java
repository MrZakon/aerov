package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.pvp.HitColorModule;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class HitColorMixin {
    @Inject(method = "getOverlay", at = @At("HEAD"), cancellable = true)
    private static void aero$replaceRedOverlay(LivingEntityRenderState state, float animation, CallbackInfoReturnable<Integer> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null || !state.hurt) return;
        HitColorModule module = runtime.module(HitColorModule.class);
        if (module != null && module.isEnabled()) cir.setReturnValue(OverlayTexture.DEFAULT_UV);
    }

    @Inject(method = "getMixColor", at = @At("HEAD"), cancellable = true)
    private void aero$customHitColor(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null || !state.hurt) return;
        HitColorModule module = runtime.module(HitColorModule.class);
        if (module != null && module.isEnabled()) cir.setReturnValue(module.color());
    }
}
