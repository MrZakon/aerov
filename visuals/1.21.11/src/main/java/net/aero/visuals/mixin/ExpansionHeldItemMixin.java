package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemRenderer.class)
public abstract class ExpansionHeldItemMixin {
    @Inject(method="renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",at=@At("HEAD"))
    private void aero$blade(LivingEntity entity,ItemStack stack,ItemDisplayContext mode,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){var m=VisualExpansion.get(SwordTrail.class);if(m!=null&&entity==net.minecraft.client.MinecraftClient.getInstance().player)m.capture(stack,matrices);}
}
