package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.SelfNametags;import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.Minecraft;import net.minecraft.client.renderer.entity.player.AvatarRenderer;import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AvatarRenderer.class) public abstract class ShowcaseNametagMixin {
 @Inject(method="shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z",at=@At("HEAD"),cancellable=true)
 private void aero$ownName(Avatar player,double distance,CallbackInfoReturnable<Boolean> cir){var mc=Minecraft.getInstance();var module=VisualExpansion.get(SelfNametags.class);if(module!=null&&module.isEnabled()&&player==mc.player&&!net.aero.visuals.render.ClientView.hud().isHidden()&&!mc.options.getCameraType().isFirstPerson()&&!mc.player.isInvisible())cir.setReturnValue(true);}
}
