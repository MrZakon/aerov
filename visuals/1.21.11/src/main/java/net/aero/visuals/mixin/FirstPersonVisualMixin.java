package net.aero.visuals.mixin;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.pvp.*;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemRenderer.class)
public abstract class FirstPersonVisualMixin {
    @Shadow private ItemStack mainHand;
    @Shadow private ItemStack offHand;

    @Inject(method="renderFirstPersonItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/util/math/MatrixStack;push()V",shift=At.Shift.AFTER))
    private void aero$viewModel(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,ItemStack stack,float equip,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){
        var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(ViewModelModule.class);if(module!=null)module.transform(matrices,hand==Hand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite());
        var arm=hand==Hand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();var shield=runtime.module(net.aero.visuals.feature.premium.PremiumFeatures.ShieldPhysics.class);if(shield!=null)shield.transform(matrices,stack,arm);
    }
    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true)
    private void aero$swing(float progress, MatrixStack matrices, int side, Arm arm, CallbackInfo ci) {
        var runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        var module = runtime.module(SwingAnimationModule.class);
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (module == null || player == null || player.isUsingItem()) return;
        ItemStack stack = arm == player.getMainArm() ? mainHand : offHand;
        if (module.transform(matrices, arm, progress, stack)) ci.cancel();
    }
    @Inject(method="renderFirstPersonItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void aero$inspect(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,ItemStack stack,float equip,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){
        if(hand!=Hand.MAIN_HAND||stack.isEmpty())return;var runtime=ClientRuntime.getNullable();if(runtime==null)return;var module=runtime.module(net.aero.visuals.feature.premium.PremiumFeatures.ItemInspect.class);if(module!=null)module.transform(matrices,player.getMainArm());
    }
}
