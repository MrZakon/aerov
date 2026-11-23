package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandRenderer.class)
public abstract class ExpansionHeldItemMixin {
    @Inject(method="renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",at=@At("HEAD"))
    private void aero$blade(LivingEntity entity,ItemStack stack,ItemDisplayContext mode,PoseStack matrices,SubmitNodeCollector queue,int light,CallbackInfo ci){var m=VisualExpansion.get(SwordTrail.class);if(m!=null&&entity==net.minecraft.client.Minecraft.getInstance().player)m.capture(stack,matrices);}
}
