package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.SelfNametags;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerEntityRenderer.class)
public abstract class ShowcaseNametagMixin {
    @Inject(method="hasLabel(Lnet/minecraft/entity/PlayerLikeEntity;D)Z",at=@At("HEAD"),cancellable=true)
    private void aero$ownName(PlayerLikeEntity player,double distance,CallbackInfoReturnable<Boolean> cir){var mc=MinecraftClient.getInstance();var module=VisualExpansion.get(SelfNametags.class);if(module!=null&&module.isEnabled()&&player==mc.player&&!mc.options.hudHidden&&!mc.options.getPerspective().isFirstPerson()&&!mc.player.isInvisible())cir.setReturnValue(true);}
}
