package net.aero.visuals.mixin;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.aero.visuals.feature.premium.PremiumPostFx;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** A separate graph pass runs after the sky and before terrain, with no native pass open. */
@Mixin(LevelRenderer.class)
public abstract class PremiumBackgroundPassMixin {
    @Shadow @Final private LevelTargetBundle targets;
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;close()V"))
    private void aero$glintsBeforeRelease(CallbackInfo ci){PremiumPostFx.flushGlints();}
    @Inject(method="addSkyPass",at=@At("RETURN"))
    private void aero$background(FrameGraphBuilder graph,CameraRenderState camera,GpuBufferSlice fog,CallbackInfo ci){
        if(!PremiumPostFx.needsBackground())return;
        var pass=graph.addPass("aero_sky_background");targets.main=pass.readsAndWrites(targets.main);pass.executes(PremiumPostFx::background);
    }
}