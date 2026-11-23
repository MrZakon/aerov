package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameOverlayRenderer.class)
public abstract class ExpansionTotemMixin {
    @Shadow private ItemStack floatingItem;
    @Inject(method="setFloatingItem",at=@At("RETURN"))
    private void aero$start(ItemStack stack,net.minecraft.util.math.random.Random random,CallbackInfo ci){var m=VisualExpansion.get(TotemAnimation.class);if(m!=null)m.start(stack);}
    @Inject(method="renderFloatingItem",at=@At("HEAD"),cancellable=true)
    private void aero$replace(MatrixStack matrices,float delta,OrderedRenderCommandQueue queue,CallbackInfo ci){var m=VisualExpansion.get(TotemAnimation.class);if(m!=null&&m.isEnabled()&&floatingItem!=null&&floatingItem.isOf(Items.TOTEM_OF_UNDYING))ci.cancel();}
}
