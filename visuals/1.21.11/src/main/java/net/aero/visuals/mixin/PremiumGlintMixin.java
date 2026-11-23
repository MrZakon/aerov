package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.PremiumPostFx;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(RenderLayers.class)
public abstract class PremiumGlintMixin {
    @Inject(method="armorEntityGlint",at=@At("RETURN"),cancellable=true)private static void aero$armor(CallbackInfoReturnable<RenderLayer> cir){cir.setReturnValue(PremiumPostFx.glint(cir.getReturnValue(),0));}
    @Inject(method="glintTranslucent",at=@At("RETURN"),cancellable=true)private static void aero$translucent(CallbackInfoReturnable<RenderLayer> cir){cir.setReturnValue(PremiumPostFx.glint(cir.getReturnValue(),1));}
    @Inject(method="glint",at=@At("RETURN"),cancellable=true)private static void aero$item(CallbackInfoReturnable<RenderLayer> cir){cir.setReturnValue(PremiumPostFx.glint(cir.getReturnValue(),2));}
    @Inject(method="entityGlint",at=@At("RETURN"),cancellable=true)private static void aero$entity(CallbackInfoReturnable<RenderLayer> cir){cir.setReturnValue(PremiumPostFx.glint(cir.getReturnValue(),3));}
}
