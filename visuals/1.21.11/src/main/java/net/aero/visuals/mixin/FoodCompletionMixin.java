package net.aero.visuals.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.component.type.ConsumableComponent;
import net.aero.visuals.feature.ExtraFeatures;
import net.aero.visuals.feature.ImportedFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Both timed and instant consumables reach this component's completion method. */
@Mixin(ConsumableComponent.class)
public abstract class FoodCompletionMixin {
    @Inject(method="finishConsumption",at=@At("HEAD"))
    private void aero$foodCompletion(World world,LivingEntity user,ItemStack stack,CallbackInfoReturnable<ItemStack> ci){
        var client=MinecraftClient.getInstance();
        if(!client.isOnThread()||client.player!=user)return;
        var action=((ConsumableComponent)(Object)this).useAction();
        if(action!=net.minecraft.item.consume.UseAction.EAT&&action!=net.minecraft.item.consume.UseAction.DRINK)return;
        var module=ImportedFeatures.get(ExtraFeatures.FoodEffects.class);
        if(module!=null)try{module.completedUse();}catch(RuntimeException failure){net.aero.visuals.AeroVisuals.LOGGER.debug("Could not play food completion effect",failure);}
    }
}
