package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.aero.visuals.render.FrameStallMonitor;
import net.aero.visuals.render.FullscreenPresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes client frame execution without altering native window callbacks or display mode. */
@Mixin(MinecraftClient.class)
public abstract class ClientFrameMixin {

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;blitToScreen()V"))
    private boolean aero$blitVisibleFullscreen(Framebuffer framebuffer) {
        return FullscreenPresentation.canPresent((MinecraftClient)(Object)this);
    }

    @Inject(method="render",at=@At("HEAD"))
    private void aero$beginFrame(boolean tick, CallbackInfo ci) {
        var client = (MinecraftClient) (Object) this;
        FullscreenPresentation.resetFrame();
        net.aero.visuals.module.impl.hud.FrameTimeHudModule.recordFrame(client);
        net.aero.visuals.render.performance.PerformanceFrames.begin(client);
        FrameStallMonitor.beginFrame(client);
        if (client.world == null) {
            net.aero.visuals.feature.ImportedPostFx.close();
            net.aero.visuals.feature.premium.PremiumPostFx.close();
        }
    }

    @Inject(method="render",at=@At("RETURN"))
    private void aero$endFrame(boolean tick,CallbackInfo ci){FullscreenPresentation.resetFrame(); net.aero.visuals.render.performance.PerformanceProbe.endFrame(); FrameStallMonitor.endFrame();}
}
