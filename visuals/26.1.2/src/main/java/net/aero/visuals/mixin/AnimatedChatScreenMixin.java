package net.aero.visuals.mixin;
import net.aero.visuals.animation.ChatMotion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(ChatScreen.class)
public abstract class AnimatedChatScreenMixin {
    @Shadow protected EditBox input;
    @Unique private boolean aero$opened;

    @Inject(method="init",at=@At("TAIL"))
    private void aero$open(CallbackInfo ci) {
        if (aero$opened) return;
        aero$opened = true;
        ChatMotion.open();
    }
    @Inject(method="extractRenderState",at=@At("HEAD"))private void aero$input(GuiGraphicsExtractor d,int x,int y,float delta,CallbackInfo ci){float t=ChatMotion.input();input.setY(d.guiHeight()-12+Math.round((1-t)*16));input.setAlpha(t);}
    @ModifyArgs(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))private void aero$background(Args args){float t=ChatMotion.input();int offset=Math.round((1-t)*16),color=args.get(4);args.set(1,(int)args.get(1)+offset);args.set(3,(int)args.get(3)+offset);args.set(4,((int)((color>>>24)*t)<<24)|(color&0xFFFFFF));}
    @Inject(method="removed",at=@At("HEAD"))
    private void aero$close(CallbackInfo ci) {
        if (!aero$opened) return;
        aero$opened = false;
        ChatMotion.close();
    }
}
