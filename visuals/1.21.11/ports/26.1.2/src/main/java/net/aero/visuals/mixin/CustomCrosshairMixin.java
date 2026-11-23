package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.pvp.CrosshairModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class CustomCrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void aero$hideDefaultCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        CrosshairModule module = runtime.module(CrosshairModule.class);
        if (module != null && module.isEnabled()) ci.cancel();
    }
}
