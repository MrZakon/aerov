package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntityRenderer.class)
public abstract class PremiumAlphaMixin {
    @Shadow public abstract Identifier getTexture(LivingEntityRenderState state);
    @Unique
    private static float aero$alpha(LivingEntityRenderState state) {
        var module = PremiumFeatures.get(PremiumFeatures.DistantAlpha.class);
        if (module == null || !module.isEnabled() || state.invisible) return 1;
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return 1;

        var eye = mc.gameRenderer.getCamera().getCameraPos();
        double halfWidth = state.width * .5;
        double dx = Math.max(0, Math.abs(eye.x - state.x) - halfWidth);
        double dz = Math.max(0, Math.abs(eye.z - state.z) - halfWidth);
        double dy = Math.max(0, Math.max(state.y - eye.y, eye.y - state.y - state.height));
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        double far = module.far.get();
        if (!(distanceSquared < far * far)) return 1;
        double near = module.near.get();
        double minimum = module.minimum.get();
        if (distanceSquared <= near * near) return (float) minimum;
        double t = (Math.sqrt(distanceSquared) - near) / (far - near);
        return (float) (minimum + (1 - minimum) * t * t * (3 - 2 * t));
    }
    @Inject(method="getRenderLayer",at=@At("RETURN"),cancellable=true)
    private void aero$translucent(LivingEntityRenderState s,boolean body,boolean ghost,boolean outline,CallbackInfoReturnable<RenderLayer> cir){if(body&&cir.getReturnValue()!=null&&aero$alpha(s)<1)cir.setReturnValue(RenderLayers.entityTranslucent(getTexture(s)));}
    @ModifyArgs(method="render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
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
