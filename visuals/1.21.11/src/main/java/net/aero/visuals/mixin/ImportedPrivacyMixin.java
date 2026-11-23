package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(TextRenderer.class)
public abstract class ImportedPrivacyMixin {
    @ModifyVariable(method={"prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;","getWidth(Ljava/lang/String;)I"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private String aero$privateString(String value){var m=ImportedFeatures.get(ImportedFeatures.Streamer.class);return m==null?value:m.filter(value);}
    @ModifyVariable(method={"prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;","getWidth(Lnet/minecraft/text/OrderedText;)I","drawWithOutline"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private OrderedText aero$privateOrdered(OrderedText value){return net.aero.visuals.feature.PrivacyText.mask(value,ImportedFeatures.get(ImportedFeatures.Streamer.class));}
}
