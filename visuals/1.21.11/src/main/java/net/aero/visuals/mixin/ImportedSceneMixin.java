package net.aero.visuals.mixin;

import net.aero.visuals.feature.*;
import net.aero.visuals.feature.premium.PremiumPostFx;
import net.aero.visuals.module.impl.pvp.AutoClickerModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GameRenderer.class)
public abstract class ImportedSceneMixin {
    @Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.render.command.RenderDispatcher entityRenderDispatcher;
    @Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.render.BufferBuilderStorage buffers;
    @Unique private final Matrix4f aero$projection = new Matrix4f();
    @Unique private boolean aero$projectionCaptured;
    @Unique private boolean aero$processed;
    @Shadow public abstract Matrix4f getBasicProjectionMatrix(float fov);
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;openGameMenu(Z)V"))
    private void aero$pauseOnFocusLoss(MinecraftClient client, boolean pauseOnly) {
        if (!AutoClickerModule.keepPlayingInBackground(client)) client.openGameMenu(pauseOnly);
    }
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void aero$zoom(Camera camera,float delta,boolean changing,CallbackInfoReturnable<Float> cir){var z=ImportedFeatures.get(ImportedFeatures.Zoom.class);if(changing&&z!=null&&z.isEnabled())cir.setReturnValue(Math.max(3,cir.getReturnValue()*z.multiplier()));}
    @Inject(method="renderWorld",at=@At("HEAD"))
    private void aero$begin(RenderTickCounter counter,CallbackInfo ci){aero$projectionCaptured=false;aero$processed=false;ImportedPostFx.beginFrame();net.aero.visuals.feature.premium.PremiumPostFx.begin();}
    @ModifyArg(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"),index=5)
    private Matrix4f aero$capture(Matrix4f projection){aero$projection.set(projection);aero$projectionCaptured=true;net.aero.visuals.feature.premium.PremiumPostFx.captureProjection(projection);return projection;}
    @Inject(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",shift=At.Shift.AFTER))
    private void aero$worldEffects(RenderTickCounter counter, CallbackInfo ci) {
        if (ImportedPostFx.needsPass(false) || PremiumPostFx.needsSceneShading()) {
            entityRenderDispatcher.render();
            buffers.getEntityVertexConsumers().draw();
            ImportedPostFx.apply(aero$matrix(), false);
        }
        if (PremiumPostFx.hasGlintMask() || PremiumPostFx.needsSceneShading()) PremiumPostFx.apply(aero$matrix(), true);
        aero$processed = true;
    }
    @Inject(method="renderWorld",at=@At("RETURN"))
    private void aero$end(RenderTickCounter counter,CallbackInfo ci){if(!aero$processed){ImportedPostFx.apply(aero$matrix(),false);net.aero.visuals.feature.premium.PremiumPostFx.apply(aero$matrix(),true);}net.aero.visuals.feature.premium.PremiumPostFx.finish(aero$matrix());ImportedPostFx.finishFrame();}
    @Inject(method="renderHand",at=@At("RETURN"))
    private void aero$hands(float delta,boolean sleeping,Matrix4f matrix,CallbackInfo ci){if(ImportedPostFx.needsPass(true)){entityRenderDispatcher.render();buffers.getEntityVertexConsumers().draw();ImportedPostFx.apply(aero$matrix(),true);net.aero.visuals.feature.premium.PremiumPostFx.apply(aero$matrix(),false);}}
    @Unique private Matrix4f aero$matrix(){return aero$projectionCaptured?aero$projection:getBasicProjectionMatrix(MinecraftClient.getInstance().options.getFov().getValue());}
    @Inject(method="close",at=@At("HEAD")) private void aero$close(CallbackInfo ci){ImportedPostFx.close();net.aero.visuals.feature.premium.PremiumPostFx.close();}
}
