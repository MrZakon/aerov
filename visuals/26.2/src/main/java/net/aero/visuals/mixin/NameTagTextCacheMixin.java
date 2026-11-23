package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.aero.visuals.render.font.NameTagTextCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagTextCacheMixin {
    @Unique private final NameTagTextCache aero$nameTags = new NameTagTextCache();

    @WrapOperation(method = "buildGroup",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer;prepareText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private Font.PreparedText aero$prepare(Font font, NameTagFeatureRenderer.Submit tag,
                                           Operation<Font.PreparedText> original) {
        Font.PreparedText prepared = aero$nameTags.prepare(font, tag.text(), tag.x(), tag.y(),
                tag.color(), false, tag.backgroundColor());
        return prepared != null ? prepared : original.call(font, tag);
    }
}
