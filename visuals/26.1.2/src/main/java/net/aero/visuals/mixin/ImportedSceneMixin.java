package net.aero.visuals.mixin;

import net.aero.visuals.feature.*;
import net.aero.visuals.feature.premium.PremiumPostFx;
import net.minecraft.client.DeltaTracker;

import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GameRenderer.class)
public abstract class ImportedSceneMixin {
    @Unique private final Matrix4f aero$projection = new Matrix4f();
    @Unique private boolean aero$projectionCaptured;
    @Unique private boolean aero$processed;
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void aero$begin(DeltaTracker counter,CallbackInfo ci){aero$projectionCaptured=false;aero$processed=false;ImportedPostFx.beginFrame();net.aero.visuals.feature.premium.PremiumPostFx.begin();}
    @ModifyArg(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"),index=3)
    private net.minecraft.client.renderer.state.level.CameraRenderState aero$capture(net.minecraft.client.renderer.state.level.CameraRenderState camera){aero$projection.set(camera.projectionMatrix);aero$projectionCaptured=true;net.aero.visuals.feature.premium.PremiumPostFx.captureProjection(camera.projectionMatrix);return camera;}
    @Inject(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",shift=At.Shift.AFTER))
    private void aero$worldEffects(DeltaTracker counter, CallbackInfo ci) {
        if (ImportedPostFx.needsPass(false)) {
            ImportedPostFx.apply(aero$matrix(), false);
        }
        if (PremiumPostFx.hasGlintMask() || PremiumPostFx.needsSceneShading()) PremiumPostFx.apply(aero$matrix(), true);
        aero$processed = true;
    }
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void aero$end(DeltaTracker counter,CallbackInfo ci){if(!aero$processed){ImportedPostFx.apply(aero$matrix(),false);net.aero.visuals.feature.premium.PremiumPostFx.apply(aero$matrix(),true);}net.aero.visuals.feature.premium.PremiumPostFx.finish(aero$matrix());ImportedPostFx.finishFrame();}
    @Inject(method="renderItemInHand",at=@At("RETURN"))
    private void aero$hands(net.minecraft.client.renderer.state.level.CameraRenderState camera, float delta, org.joml.Matrix4fc matrix, CallbackInfo ci) {
        if (!ImportedPostFx.needsPass(true)) return;
        ImportedPostFx.apply(camera.projectionMatrix, true);
        PremiumPostFx.apply(camera.projectionMatrix, false);
    }
    @Unique private Matrix4f aero$matrix(){return aero$projectionCaptured?aero$projection:new Matrix4f();}
    @Inject(method="close",at=@At("HEAD")) private void aero$close(CallbackInfo ci){ImportedPostFx.close();net.aero.visuals.feature.premium.PremiumPostFx.close();}
}
