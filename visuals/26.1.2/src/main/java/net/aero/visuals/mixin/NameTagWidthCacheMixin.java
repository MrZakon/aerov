package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.LinkedHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.renderer.feature.NameTagFeatureRenderer$Storage")
public abstract class NameTagWidthCacheMixin {
    @Unique private final LinkedHashMap<Component, Integer> aero$nameWidths = new LinkedHashMap<>(32, 0.75f, true);
    @Unique private Font aero$font;
    @Unique private Language aero$language;

    @WrapOperation(method = "add",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int aero$nameWidth(Font font, FormattedText text, Operation<Integer> original) {
        Language language = Language.getInstance();
        if (font != aero$font || language != aero$language) {
            aero$nameWidths.clear();
            aero$font = font;
            aero$language = language;
        }
        if (!(text instanceof Component component)) return original.call(font, text);
        Integer width = aero$nameWidths.get(component);
        if (width != null) return width;
        width = original.call(font, text);
        if (aero$nameWidths.size() >= 256) aero$nameWidths.pollFirstEntry();
        aero$nameWidths.put(aero$snapshotName(component), width);
        return width;
    }

    @Unique
    private static Component aero$snapshotName(Component text) {
        var copy = text.plainCopy().setStyle(text.getStyle());
        for (var sibling : text.getSiblings()) copy.append(aero$snapshotName(sibling));
        return copy;
    }
}
