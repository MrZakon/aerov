package net.aero.visuals.mixin;
import net.aero.visuals.animation.ChatMotion;
import net.aero.visuals.animation.AnimatedChatBackend;
import net.minecraft.client.gui.hud.*;
import net.minecraft.text.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(ChatHud.class)
public abstract class AnimatedChatHudMixin {
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;
    @Unique private boolean aero$adding;
    @Unique private Set<ChatHudLine.Visible> aero$before;
    @Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("HEAD"))private void aero$start(CallbackInfo ci){aero$adding=true;}
    @Inject(method="addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",at=@At("RETURN"))private void aero$stop(CallbackInfo ci){aero$adding=false;}
    @Inject(method="addVisibleMessage",at=@At("HEAD"))private void aero$before(ChatHudLine message,CallbackInfo ci){if(aero$adding){aero$before=Collections.newSetFromMap(new IdentityHashMap<>());aero$before.addAll(visibleMessages);}}
    @Inject(method="addVisibleMessage",at=@At("RETURN"))private void aero$after(ChatHudLine message,CallbackInfo ci){if(aero$adding&&aero$before!=null){for(var line:visibleMessages)if(!aero$before.contains(line))ChatMotion.arrived(line.content());aero$before=null;}}
    @ModifyVariable(method="render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private ChatHud.Backend aero$backend(ChatHud.Backend backend){return new AnimatedChatBackend(backend,scrolledLines>0);}
}
