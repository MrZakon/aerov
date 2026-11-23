package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class ExpansionTotemStartMixin {
    @Inject(method="displayItemActivation",at=@At("RETURN"))
    private void aero$start(ItemStack item,CallbackInfo ci){var m=VisualExpansion.get(TotemAnimation.class);if(m!=null)m.start(item);}
}