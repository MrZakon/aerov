package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(MouseHandler.class)
public abstract class ImportedMouseMixin {
    @ModifyArgs(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void aero$sensitivity(Args args){var module=ImportedFeatures.get(ImportedFeatures.Zoom.class);if(module==null||!module.isEnabled())return;double factor=Math.max(.05,module.multiplier());args.set(0,(double)args.get(0)*factor);args.set(1,(double)args.get(1)*factor);}
}
