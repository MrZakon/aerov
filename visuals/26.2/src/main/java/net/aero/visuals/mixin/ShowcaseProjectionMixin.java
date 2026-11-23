package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.AspectRatio;import net.aero.visuals.feature.enhanced.VisualExpansion;import net.minecraft.client.Minecraft;import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(Camera.class) public abstract class ShowcaseProjectionMixin {
 @ModifyArgs(method="setupPerspective",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
 private void aero$aspect(Args args){var mc=Minecraft.getInstance();var module=VisualExpansion.get(AspectRatio.class);if(module!=null&&module.isEnabled()&&mc.level!=null&&mc.player!=null)args.set(3,(float)args.get(4)*module.ratio());}
}
