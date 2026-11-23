package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntityRenderer.class)
public abstract class PremiumAlphaMixin {
    @Shadow public abstract Identifier getTextureLocation(LivingEntityRenderState state);
    @Unique
    private static float aero$alpha(LivingEntityRenderState state) {
        var module = PremiumFeatures.get(PremiumFeatures.DistantAlpha.class);
        if (module == null || !module.isEnabled() || state.isInvisible) return 1;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return 1;

        var eye = mc.gameRenderer.getMainCamera().position();
        double halfWidth = state.boundingBoxWidth * .5;
        double dx = Math.max(0, Math.abs(eye.x - state.x) - halfWidth);
        double dz = Math.max(0, Math.abs(eye.z - state.z) - halfWidth);
        double dy = Math.max(0, Math.max(state.y - eye.y, eye.y - state.y - state.boundingBoxHeight));
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        double far = module.far.get();
        if (!(distanceSquared < far * far)) return 1;
        double near = module.near.get();
        double minimum = module.minimum.get();
        if (distanceSquared <= near * near) return (float) minimum;
        double t = (Math.sqrt(distanceSquared) - near) / (far - near);
        return (float) (minimum + (1 - minimum) * t * t * (3 - 2 * t));
    }
    @Inject(method="getRenderType",at=@At("RETURN"),cancellable=true)
    private void aero$translucent(LivingEntityRenderState s,boolean body,boolean ghost,boolean outline,CallbackInfoReturnable<RenderType> cir){if(body&&cir.getReturnValue()!=null&&aero$alpha(s)<1)cir.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(s)));}
    @ModifyArgs(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void aero$fade(Args args) {
        if (args.get(1) instanceof LivingEntityRenderState state) {
            float alpha = aero$alpha(state);
            if (alpha < 1) {
                int color = args.get(6);
                args.set(6, (Math.round((color >>> 24) * alpha) << 24) | (color & 0xFFFFFF));
            }
        }
    }
}
