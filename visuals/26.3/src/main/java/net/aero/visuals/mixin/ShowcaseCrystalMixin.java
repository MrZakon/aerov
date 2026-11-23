package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.*;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.model.Model;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(EndCrystalRenderer.class)
public abstract class ShowcaseCrystalMixin {
    @Redirect(method="submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
    private void aero$material(SubmitNodeCollector queue,Model model,Object state,PoseStack pose,Identifier texture,int light,int overlay,int color){var m=VisualExpansion.get(Materials.Crystals.class);if(m==null||!m.isEnabled()){queue.submitModel(model,state,pose,texture,light,overlay,color);return;}queue.submitModel(model,state,pose,SceneGeometry.material(Materials.Crystals.TEXTURE),m.animation(),m.color(),color,null,0);}
}