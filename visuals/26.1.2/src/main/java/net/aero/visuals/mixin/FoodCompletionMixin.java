package net.aero.visuals.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.Consumable;
import net.aero.visuals.feature.ExtraFeatures;
import net.aero.visuals.feature.ImportedFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Both timed and instant consumables reach this component's completion method. */
@Mixin(Consumable.class)
public abstract class FoodCompletionMixin {
    @Inject(method="onConsume",at=@At("HEAD"))
    private void aero$foodCompletion(Level world,LivingEntity user,ItemStack stack,CallbackInfoReturnable<ItemStack> ci){
        var client=Minecraft.getInstance();
        if(!client.isSameThread()||client.player!=user)return;
        var action=((Consumable)(Object)this).animation();
        if(action!=net.minecraft.world.item.ItemUseAnimation.EAT&&action!=net.minecraft.world.item.ItemUseAnimation.DRINK)return;
        var module=ImportedFeatures.get(ExtraFeatures.FoodEffects.class);
        if(module!=null)try{module.completedUse();}catch(RuntimeException failure){net.aero.visuals.AeroVisuals.LOGGER.debug("Could not play food completion effect",failure);}
    }
}
