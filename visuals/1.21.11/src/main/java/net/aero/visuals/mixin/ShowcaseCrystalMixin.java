package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.*;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(EndCrystalEntityRenderer.class)
public abstract class ShowcaseCrystalMixin {
    @ModifyArgs(method="render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    private void aero$material(Args args){var module=VisualExpansion.get(Materials.Crystals.class);if(module==null||!module.isEnabled())return;args.set(3,SceneGeometry.material(Materials.Crystals.TEXTURE));args.set(4,module.animation());args.set(5,module.color());}
}
