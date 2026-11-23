package net.aero.visuals.mixin;
import net.aero.visuals.animation.ChatMotion;
import net.aero.visuals.animation.AnimatedChatBackend;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;


import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(ChatComponent.class)
public abstract class AnimatedChatHudMixin {
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Unique private boolean aero$adding;
    @Unique private GuiMessage.Line aero$previousFirst;
    @Inject(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",at=@At("HEAD"))private void aero$start(CallbackInfo ci){aero$adding=true;}
    @Inject(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",at=@At("RETURN"))private void aero$stop(CallbackInfo ci){aero$adding=false;}
    @Inject(method="addMessageToDisplayQueue",at=@At("HEAD"))
    private void aero$before(GuiMessage message, CallbackInfo ci) {
        if (aero$adding) aero$previousFirst = trimmedMessages.isEmpty() ? null : trimmedMessages.getFirst();
    }

    @Inject(method="addMessageToDisplayQueue",at=@At("RETURN"))
    private void aero$after(GuiMessage message, CallbackInfo ci) {
        if (!aero$adding) return;
        var previousFirst = aero$previousFirst;
        aero$previousFirst = null;
        if (ChatMotion.reduced()) return;
        for (var line : trimmedMessages) {
            if (line == previousFirst) break;
            ChatMotion.arrived(line.content());
        }
    }
    @ModifyVariable(method="extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private ChatComponent.ChatGraphicsAccess aero$backend(ChatComponent.ChatGraphicsAccess backend){return chatScrollbarPos > 0 || !ChatMotion.hasActiveMessages() ? backend : new AnimatedChatBackend(backend);}
}
