package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ScreenEffectRenderer.class)
public abstract class ExpansionTotemMixin {
    @Shadow private ItemStack itemActivationItem;
    @Inject(method="displayItemActivation",at=@At("RETURN"))
    private void aero$start(ItemStack stack,net.minecraft.util.RandomSource random,CallbackInfo ci){var m=VisualExpansion.get(TotemAnimation.class);if(m!=null)m.start(stack);}
    @Inject(method="renderItemActivationAnimation",at=@At("HEAD"),cancellable=true)
    private void aero$replace(PoseStack matrices,float delta,SubmitNodeCollector queue,CallbackInfo ci){var m=VisualExpansion.get(TotemAnimation.class);if(m!=null&&m.isEnabled()&&itemActivationItem!=null&&itemActivationItem.is(Items.TOTEM_OF_UNDYING))ci.cancel();}
}
