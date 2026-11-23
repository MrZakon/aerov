package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.aero.visuals.feature.premium.PremiumFeatures;
import net.aero.visuals.feature.premium.WorldEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class PremiumJumpMixin {
    @Inject(method="jump",at=@At("RETURN"))
    private void aero$jumpEffects(CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        var rings = ImportedFeatures.get(ImportedFeatures.JumpCircles.class);
        if (rings != null) rings.jump();
        var wave = PremiumFeatures.get(WorldEffects.WaveEffect.class);
        if (wave != null) wave.jump();
    }
}
