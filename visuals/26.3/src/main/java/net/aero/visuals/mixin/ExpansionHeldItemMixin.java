package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ExpansionHeldItemMixin {
    @Inject(method="submitArmWithItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void aero$blade(PlayerRenderState player,FirstPersonHandsAndItemsRenderState hands,float delta,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,SubmitNodeCollector queue,int light,CallbackInfo ci){
        if(hand==InteractionHand.MAIN_HAND){var module=VisualExpansion.get(SwordTrail.class);if(module!=null)module.capture(stack,pose);}
    }
}