package net.aero.visuals.mixin;

import net.aero.visuals.render.StbImageResizeCompat;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NativeImage.class)
public abstract class NativeImageResizeMixin {
    @Redirect(method = "resizeSubRectTo", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/stb/STBImageResize;nstbir_resize_uint8(JIIIJIIII)I", remap = false))
    private int aero$resizeImage(long input, int inputWidth, int inputHeight, int inputStride,
                                 long output, int outputWidth, int outputHeight, int outputStride, int channels) {
        return StbImageResizeCompat.resize(input, inputWidth, inputHeight, inputStride,
                output, outputWidth, outputHeight, outputStride, channels);
    }
}
