package net.aero.visuals.mixin;
import net.aero.visuals.render.TargetMarkerProjection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class TargetMarkerProjectionMixin {
    @Shadow @Final private net.minecraft.client.renderer.state.GameRenderState gameRenderState;
    // GUI extraction precedes world submission in 26.1.2: capture this frame's camera first.
    @Inject(method="extractCamera",at=@At("RETURN"))
    private void aero$capture(CallbackInfo ci){TargetMarkerProjection.capture(gameRenderState.levelRenderState.cameraRenderState);}
}
