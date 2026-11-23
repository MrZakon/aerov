package net.aero.visuals.mixin;

import net.aero.visuals.render.FrameStallMonitor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes client frame execution without altering native window callbacks or display mode. */
@Mixin(Minecraft.class)
public abstract class ClientFrameMixin {
    @Inject(method="runTick",at=@At("HEAD"))
    private void aero$beginFrame(boolean tick, CallbackInfo ci) {
        var client = (Minecraft) (Object) this;
        net.aero.visuals.module.impl.hud.FrameTimeHudModule.recordFrame(client);
        net.aero.visuals.render.performance.PerformanceFrames.begin(client);
        FrameStallMonitor.beginFrame(client);
        if (client.level == null) {
            net.aero.visuals.feature.ImportedPostFx.close();
            net.aero.visuals.feature.premium.PremiumPostFx.close();
        }
    }

    @Inject(method="runTick",at=@At("RETURN"))
    private void aero$endFrame(boolean tick,CallbackInfo ci){net.aero.visuals.render.performance.PerformanceProbe.endFrame(); FrameStallMonitor.endFrame();}
}
