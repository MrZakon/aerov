package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ScreenEffectRenderer.class)
public abstract class ExpansionTotemMixin {
    @Inject(method="renderItemActivationAnimation",at=@At("HEAD"),cancellable=true)
    private void aero$replace(PlayerRenderState player,PoseStack pose,float delta,SubmitNodeCollector queue,CallbackInfo ci){var m=VisualExpansion.get(TotemAnimation.class);var activation=player.itemActivation;if(m!=null&&m.isEnabled()&&activation!=null&&activation.item.is(Items.TOTEM_OF_UNDYING))ci.cancel();}
}