package net.aero.visuals.mixin;

import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NativeImage.class)
public interface MultiAccountImageAccessor {
    @Accessor("pointer")
    long aero$pixelAddress();
}
