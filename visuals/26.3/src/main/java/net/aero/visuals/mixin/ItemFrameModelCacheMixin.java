package net.aero.visuals.mixin;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.BlockModelSet;
import net.minecraft.client.resources.model.ModelManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelResolver.class)
public abstract class ItemFrameModelCacheMixin {
    @Shadow @Final private ModelManager modelManager;

    @Unique private final BlockModelRenderState[] aero$itemFrames = new BlockModelRenderState[4];
    @Unique private BlockModelSet aero$cachedModels;

    @Inject(method = "updateForItemFrame", at = @At("HEAD"), cancellable = true)
    private void aero$reuseFrameModel(BlockModelRenderState target, boolean glowing, boolean map, CallbackInfo ci) {
        BlockModelSet models = modelManager.getBlockModelSet();
        if (models != aero$cachedModels) {
            Arrays.fill(aero$itemFrames, null);
            aero$cachedModels = models;
        }

        BlockModelRenderState cached = aero$itemFrames[(glowing ? 2 : 0) | (map ? 1 : 0)];
        if (cached == null) return;

        var template = (ItemFrameModelStateAccessor) cached;
        var output = (ItemFrameModelStateAccessor) target;
        target.clear();
        output.aero$setParts(template.aero$getParts());
        output.aero$setTransform(template.aero$getTransform());
        output.aero$setRenderType(template.aero$getRenderType());
        var tints = template.aero$getTints();
        if (tints != null && !tints.isEmpty()) target.tintLayers().addAll(tints);
        ci.cancel();
    }

    @Inject(method = "updateForItemFrame", at = @At("RETURN"))
    private void aero$rememberFrameModel(BlockModelRenderState target, boolean glowing, boolean map, CallbackInfo ci) {
        int index = (glowing ? 2 : 0) | (map ? 1 : 0);
        if (aero$itemFrames[index] != null) return;
        var rendered = (ItemFrameModelStateAccessor) target;
        // Special renderers can carry mutable state. Leave these on the regular path.
        if (rendered.aero$getSpecialRenderer() != null || rendered.aero$getSpecialTransform() != null) return;

        BlockModelRenderState cached = new BlockModelRenderState();
        var template = (ItemFrameModelStateAccessor) cached;
        var parts = rendered.aero$getParts();
        template.aero$setParts(parts == null ? null : List.copyOf(parts));
        var transform = rendered.aero$getTransform();
        template.aero$setTransform(transform == null ? null : new Matrix4f(transform));
        template.aero$setRenderType(rendered.aero$getRenderType());
        var tints = rendered.aero$getTints();
        if (tints != null && !tints.isEmpty()) cached.tintLayers().addAll(tints);
        aero$itemFrames[index] = cached;
    }
}
