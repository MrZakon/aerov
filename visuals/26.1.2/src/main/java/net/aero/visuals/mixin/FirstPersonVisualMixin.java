package net.aero.visuals.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.pvp.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonVisualMixin {
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    @Inject(method="renderArmWithItem",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER))
    private void aero$viewModel(AbstractClientPlayer player,float delta,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack matrices,SubmitNodeCollector queue,int light,CallbackInfo ci){
        var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(ViewModelModule.class);if(module!=null)module.transform(matrices,hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite());
        var arm=hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();var shield=runtime.module(net.aero.visuals.feature.premium.PremiumFeatures.ShieldPhysics.class);if(shield!=null)shield.transform(matrices,stack,arm);
    }
    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true)
    private void aero$swing(float progress, PoseStack matrices, int side, HumanoidArm arm, CallbackInfo ci) {
        var runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        var module = runtime.module(SwingAnimationModule.class);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (module == null || player == null || player.isUsingItem()) return;
        ItemStack stack = arm == player.getMainArm() ? mainHandItem : offHandItem;
        if (module.transform(matrices, arm, progress, stack)) ci.cancel();
    }
    @Inject(method="renderArmWithItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void aero$inspect(AbstractClientPlayer player,float delta,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack matrices,SubmitNodeCollector queue,int light,CallbackInfo ci){
        if(hand!=InteractionHand.MAIN_HAND||stack.isEmpty())return;var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(net.aero.visuals.feature.premium.PremiumFeatures.ItemInspect.class);if(module!=null)module.transform(matrices,player.getMainArm());
    }
}
