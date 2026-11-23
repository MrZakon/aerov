package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientPlayerInteractionManager.class)
public abstract class PremiumBlockMixin {
    @Unique private int aero$blockColor;
    @Inject(method="breakBlock",at=@At("HEAD"))private void aero$before(BlockPos p,CallbackInfoReturnable<Boolean> cir){var mc=MinecraftClient.getInstance();if(mc.world!=null)aero$blockColor=mc.world.getBlockState(p).getMapColor(mc.world,p).color;}
    @Inject(method="breakBlock",at=@At("RETURN"))private void aero$broken(BlockPos p,CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValue()){var m=PremiumFeatures.get(WorldEffects.BlockParticles.class);if(m!=null)m.broken(Vec3d.ofCenter(p),aero$blockColor);}}
}
