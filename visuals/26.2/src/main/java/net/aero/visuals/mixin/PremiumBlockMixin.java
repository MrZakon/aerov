package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MultiPlayerGameMode.class)
public abstract class PremiumBlockMixin {
    @Unique private int aero$blockColor;
    @Inject(method="destroyBlock",at=@At("HEAD"))private void aero$before(BlockPos p,CallbackInfoReturnable<Boolean> cir){var mc=Minecraft.getInstance();if(mc.level!=null)aero$blockColor=mc.level.getBlockState(p).getMapColor(mc.level,p).col;}
    @Inject(method="destroyBlock",at=@At("RETURN"))private void aero$broken(BlockPos p,CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValue()){var m=PremiumFeatures.get(WorldEffects.BlockParticles.class);if(m!=null)m.broken(Vec3.atCenterOf(p),aero$blockColor);}}
}
