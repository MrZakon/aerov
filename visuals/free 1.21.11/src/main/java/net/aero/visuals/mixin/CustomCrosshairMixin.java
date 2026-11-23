package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.pvp.CrosshairModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class CustomCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void aero$hideDefaultCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        CrosshairModule module = runtime.module(CrosshairModule.class);
        if (module != null && module.isEnabled()) ci.cancel();
    }
}
