package net.aero.visuals.mixin;

import net.aero.visuals.feature.*;
import net.aero.visuals.feature.premium.PremiumPostFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GameRenderer.class)
public abstract class ImportedSceneMixin {
    @Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.renderer.feature.FeatureRenderDispatcher featureRenderDispatcher;
    @Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.renderer.RenderBuffers renderBuffers;
    @Shadow @org.spongepowered.asm.mixin.Final private RenderTarget mainRenderTarget;
    @Shadow @org.spongepowered.asm.mixin.Final private RenderTarget hud3DTarget;
    @Unique private final Matrix4f aero$projection = new Matrix4f();
    @Unique private boolean aero$projectionCaptured;
    @Unique private boolean aero$processed;
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void aero$begin(CallbackInfo ci){aero$projectionCaptured=false;aero$processed=false;ImportedPostFx.beginFrame();net.aero.visuals.feature.premium.PremiumPostFx.begin();}
    @ModifyArg(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"),index=2)
    private net.minecraft.client.renderer.state.level.CameraRenderState aero$capture(net.minecraft.client.renderer.state.level.CameraRenderState camera){aero$projection.set(camera.projectionMatrix);aero$projectionCaptured=true;net.aero.visuals.feature.premium.PremiumPostFx.captureProjection(camera.projectionMatrix);return camera;}
    @Inject(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",shift=At.Shift.AFTER))
    private void aero$worldEffects(CallbackInfo ci){if (ImportedPostFx.needsPass(false)) ImportedPostFx.apply(aero$matrix(), false);
        if (PremiumPostFx.hasGlintMask() || PremiumPostFx.needsSceneShading()) PremiumPostFx.apply(aero$matrix(), true);
        aero$processed = true;}
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void aero$end(CallbackInfo ci){if(!aero$processed){ImportedPostFx.apply(aero$matrix(),false);net.aero.visuals.feature.premium.PremiumPostFx.apply(aero$matrix(),true);}net.aero.visuals.feature.premium.PremiumPostFx.finish(aero$matrix());}
    @Inject(method="render3dHud",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;close()V",ordinal=0))
    private void aero$hands(net.minecraft.client.renderer.state.level.CameraRenderState camera,net.minecraft.client.renderer.state.level.PlayerRenderState player,net.minecraft.client.renderer.state.OptionsRenderState options,boolean separateHudDepth,CallbackInfo ci) {
        var depth = (separateHudDepth ? hud3DTarget : mainRenderTarget).getDepthTextureView();
        if (ImportedPostFx.needsPass(true)) ImportedPostFx.apply(camera.projectionMatrix, true, depth);
        if (PremiumPostFx.hasGlintMask()) {
            PremiumPostFx.flushGlints(depth);
            PremiumPostFx.apply(camera.projectionMatrix, false);
        }
    }
    @Unique private Matrix4f aero$matrix(){return aero$projectionCaptured?aero$projection:new Matrix4f();}
    @Inject(method="close",at=@At("HEAD")) private void aero$close(CallbackInfo ci){ImportedPostFx.close();net.aero.visuals.feature.premium.PremiumPostFx.close();}
}
