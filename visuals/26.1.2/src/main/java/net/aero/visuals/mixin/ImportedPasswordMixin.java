package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatScreen.class)
public abstract class ImportedPasswordMixin {
    @Shadow protected EditBox input;
    @Inject(method="init",at=@At("TAIL"))private void aero$mask(CallbackInfo ci){input.addFormatter((text,first)->{var m=ImportedFeatures.get(ImportedFeatures.Streamer.class);return FormattedCharSequence.forward(m==null?text:m.input(input.getValue()).substring(Math.min(first,input.getValue().length()),Math.min(input.getValue().length(),first+text.length())),Style.EMPTY);});}
}
