package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.PremiumFeatures;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CloudRenderer.class)
public abstract class PremiumCloudMixin {
    @Inject(method="renderClouds",at=@At("HEAD"),cancellable=true)
    private void aero$clouds(CallbackInfo ci){var shading=PremiumFeatures.get(PremiumFeatures.Shading.class);if(PremiumFeatures.on(PremiumFeatures.SkyShader.class)||shading!=null&&shading.isEnabled()&&shading.clouds.get()>.001)ci.cancel();}
}
