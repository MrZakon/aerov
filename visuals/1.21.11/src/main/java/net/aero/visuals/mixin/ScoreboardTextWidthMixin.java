package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.LinkedHashMap;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameHud.class)
public abstract class ScoreboardTextWidthMixin {
    @Unique private final LinkedHashMap<Text, Integer> aero$scoreWidths = new LinkedHashMap<>(32, 0.75f, true);
    @Unique private TextRenderer aero$font;
    @Unique private Language aero$language;

    @WrapOperation(method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", "method_55439"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I"))
    private int aero$scoreWidth(TextRenderer font, StringVisitable text, Operation<Integer> original) {
        Language language = Language.getInstance();
        if (font != aero$font || language != aero$language) {
            aero$scoreWidths.clear();
            aero$font = font;
            aero$language = language;
        }
        if (!(text instanceof Text component)) return original.call(font, text);
        Integer width = aero$scoreWidths.get(component);
        if (width != null) return width;
        width = original.call(font, text);
        if (aero$scoreWidths.size() >= 256) aero$scoreWidths.pollFirstEntry();
        aero$scoreWidths.put(aero$snapshotScore(component), width);
        return width;
    }

    @Unique
    private static Text aero$snapshotScore(Text text) {
        var copy = text.copyContentOnly().setStyle(text.getStyle());
        for (var sibling : text.getSiblings()) copy.append(aero$snapshotScore(sibling));
        return copy;
    }
}
