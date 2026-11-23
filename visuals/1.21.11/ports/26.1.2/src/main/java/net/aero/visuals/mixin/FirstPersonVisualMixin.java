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
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonVisualMixin {
    @Inject(method="renderArmWithItem",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER))
    private void aero$viewModel(AbstractClientPlayer player,float delta,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack matrices,SubmitNodeCollector queue,int light,CallbackInfo ci){
        var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(ViewModelModule.class);if(module!=null)module.transform(matrices,hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite());
    }
    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true)
    private void aero$swing(float progress,PoseStack matrices,int side,HumanoidArm arm,CallbackInfo ci){var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(SwingAnimationModule.class);var player=net.minecraft.client.Minecraft.getInstance().player;if(module!=null&&module.isEnabled()&&player!=null&&!player.isUsingItem()){module.transform(matrices,arm,progress);ci.cancel();}}
}
