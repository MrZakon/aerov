package net.aero.visuals.mixin;
import net.aero.visuals.render.TargetMarkerProjection;
import net.minecraft.client.render.*;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.*;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WorldRenderer.class)
public abstract class TargetMarkerProjectionMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void aero$capture(ObjectAllocator allocator,RenderTickCounter counter,boolean outline,Camera camera,Matrix4f view,Matrix4f projection,Matrix4f skyProjection,GpuBufferSlice fog,Vector4f fogColor,boolean sky,CallbackInfo ci){TargetMarkerProjection.capture(view,projection,camera);}

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderMain(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/render/state/WorldRenderState;Lnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/util/profiler/Profiler;)V"),
            index = 1)
    private Frustum aero$capturePetFrustum(Frustum frustum) {
        net.aero.visuals.feature.ImportedModels.setPetFrustum(frustum);
        return frustum;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void aero$clearPetFrustum(CallbackInfo ci) {
        net.aero.visuals.feature.ImportedModels.setPetFrustum(null);
    }
}
