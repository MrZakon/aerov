package net.aero.visuals.mixin;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ChatComponent.class)
public abstract class PrivacyChatMixin {
    @ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Component aero$maskBeforeWrapping(Component message){return net.aero.visuals.feature.PrivacyText.mask(message);}
}
