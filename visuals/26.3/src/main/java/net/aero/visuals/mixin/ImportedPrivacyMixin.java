package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Font.class)
public abstract class ImportedPrivacyMixin {
    @ModifyVariable(method={"prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;","width(Ljava/lang/String;)I"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private String aero$privateString(String value){var m=ImportedFeatures.get(ImportedFeatures.Streamer.class);return m==null?value:m.filter(value);}
    @ModifyVariable(method={"prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;","width(Lnet/minecraft/util/FormattedCharSequence;)I","prepare8xTextOutline"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private FormattedCharSequence aero$privateOrdered(FormattedCharSequence value){return net.aero.visuals.feature.PrivacyText.mask(value,ImportedFeatures.get(ImportedFeatures.Streamer.class));}
}
