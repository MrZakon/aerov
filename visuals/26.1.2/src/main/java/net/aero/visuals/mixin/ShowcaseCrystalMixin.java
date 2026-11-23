package net.aero.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.feature.showcase.Materials;
import net.aero.visuals.feature.showcase.SceneGeometry;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndCrystalRenderer.class)
public abstract class ShowcaseCrystalMixin {
    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
    )
    private <S> void aero$material(SubmitNodeCollector queue, Model<? super S> model, S state, PoseStack pose,
                                    Identifier texture, int light, int overlay, int outline,
                                    ModelFeatureRenderer.CrumblingOverlay crumbling) {
        var module = VisualExpansion.get(Materials.Crystals.class);
        if (module == null || !module.isEnabled()) {
            queue.submitModel(model, state, pose, texture, light, overlay, outline, crumbling);
            return;
        }
        queue.submitModel(model, state, pose, SceneGeometry.material(Materials.Crystals.TEXTURE),
                module.animation(), module.color(), outline, crumbling);
    }
}
