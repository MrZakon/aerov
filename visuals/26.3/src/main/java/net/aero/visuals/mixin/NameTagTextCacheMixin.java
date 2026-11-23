package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.aero.visuals.render.font.NameTagTextCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextFeatureRenderer.class)
public abstract class NameTagTextCacheMixin {
    @WrapOperation(method = "renderText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static Font.PreparedText aero$prepareName(Font font, FormattedCharSequence text, float x, float y,
                                                      int color, boolean shadow, boolean inverseDepth,
                                                      int background, Operation<Font.PreparedText> original) {
        if (text instanceof NameTagTextCache.CachedSequence cached) {
            return cached.prepare(font, x, y, color, shadow, inverseDepth, background);
        }
        return original.call(font, text, x, y, color, shadow, inverseDepth, background);
    }
}
