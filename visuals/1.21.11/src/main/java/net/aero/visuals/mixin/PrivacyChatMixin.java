package net.aero.visuals.mixin;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ChatHud.class)
public abstract class PrivacyChatMixin {
    @ModifyVariable(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Text aero$maskBeforeWrapping(Text message){return net.aero.visuals.feature.PrivacyText.mask(message);}
}
