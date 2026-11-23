package net.aero.visuals.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.pvp.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonVisualMixin {
    @Inject(method="submitArmWithItem",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER))
    private void aero$viewModel(net.minecraft.client.renderer.state.level.PlayerRenderState player,net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands,float delta,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack matrices,SubmitNodeCollector queue,int light,CallbackInfo ci){
        var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(ViewModelModule.class);if(module!=null)module.transform(matrices,hand==InteractionHand.MAIN_HAND?player.avatarRenderState.mainArm:player.avatarRenderState.mainArm.getOpposite());
        var arm=hand==InteractionHand.MAIN_HAND?player.avatarRenderState.mainArm:player.avatarRenderState.mainArm.getOpposite();var shield=runtime.module(net.aero.visuals.feature.premium.PremiumFeatures.ShieldPhysics.class);if(shield!=null)shield.transform(matrices,stack,arm);
    }
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
    private void aero$swing(FirstPersonHandsAndItemsRenderer renderer, float progress, PoseStack matrices,
                            int side, HumanoidArm arm, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        var runtime = ClientRuntime.getNullable();
        var module = runtime == null ? null : runtime.module(SwingAnimationModule.class);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (module != null && player != null && !player.isUsingItem() && module.transform(matrices, arm, progress, stack)) return;
        original.call(renderer, progress, matrices, side, arm);
    }
    @Inject(method="submitArmWithItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void aero$inspect(net.minecraft.client.renderer.state.level.PlayerRenderState player,net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands,float delta,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack matrices,SubmitNodeCollector queue,int light,CallbackInfo ci){
        if(hand!=InteractionHand.MAIN_HAND||stack.isEmpty())return;var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(net.aero.visuals.feature.premium.PremiumFeatures.ItemInspect.class);if(module!=null)module.transform(matrices,player.avatarRenderState.mainArm);
    }
}
