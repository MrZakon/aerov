package net.aero.visuals.mixin;

import net.aero.visuals.module.impl.misc.NightVisionModule;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightmapTextureManager.class)
public abstract class NightVisionLightmapMixin {
    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 3), index = 0)
    private float aero$nightVision(float vanilla) {
        return NightVisionModule.lightmapIntensity(vanilla);
    }
}
