package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatScreen.class)
public abstract class ImportedPasswordMixin {
    @Shadow protected TextFieldWidget chatField;
    @Inject(method="init",at=@At("TAIL"))private void aero$mask(CallbackInfo ci){chatField.addFormatter((text,first)->{var m=ImportedFeatures.get(ImportedFeatures.Streamer.class);return OrderedText.styledForwardsVisitedString(m==null?text:m.input(chatField.getText()).substring(Math.min(first,chatField.getText().length()),Math.min(chatField.getText().length(),first+text.length())),Style.EMPTY);});}
}
