package net.aero.visuals.mixin;
import net.aero.visuals.animation.ChatMotion;
import net.aero.visuals.animation.AnimatedChatBackend;
import net.minecraft.client.gui.hud.*;
import net.minecraft.text.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(ChatHud.class)
public abstract class AnimatedChatHudMixin {
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;
    @Unique private boolean aero$adding;
    @Unique private ChatHudLine.Visible aero$previousFirst;
    @Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("HEAD"))private void aero$start(CallbackInfo ci){aero$adding=true;}
    @Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("RETURN"))private void aero$stop(CallbackInfo ci){aero$adding=false;}
    @Inject(method="addVisibleMessage",at=@At("HEAD"))
    private void aero$before(ChatHudLine message, CallbackInfo ci) {
        if (aero$adding) aero$previousFirst = visibleMessages.isEmpty() ? null : visibleMessages.getFirst();
    }

    @Inject(method="addVisibleMessage",at=@At("RETURN"))
    private void aero$after(ChatHudLine message, CallbackInfo ci) {
        if (!aero$adding) return;
        var previousFirst = aero$previousFirst;
        aero$previousFirst = null;
        if (ChatMotion.reduced()) return;
        for (var line : visibleMessages) {
            if (line == previousFirst) break;
            ChatMotion.arrived(line.content());
        }
    }
    @ModifyVariable(method="render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private ChatHud.Backend aero$backend(ChatHud.Backend backend){return scrolledLines > 0 || !ChatMotion.hasActiveMessages() ? backend : new AnimatedChatBackend(backend);}
}
