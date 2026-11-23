package net.aero.visuals.mixin;
import net.aero.visuals.animation.ChatMotion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(ChatScreen.class)
public abstract class AnimatedChatScreenMixin {
    @Shadow protected TextFieldWidget chatField;
    @Unique private boolean aero$opened;

    @Inject(method="init",at=@At("TAIL"))
    private void aero$open(CallbackInfo ci) {
        if (aero$opened) return;
        aero$opened = true;
        ChatMotion.open();
    }
    @Inject(method="render",at=@At("HEAD"))private void aero$input(DrawContext d,int x,int y,float delta,CallbackInfo ci){float t=ChatMotion.input();chatField.setY(d.getScaledWindowHeight()-12+Math.round((1-t)*16));chatField.setAlpha(t);}
    @ModifyArgs(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))private void aero$background(Args args){float t=ChatMotion.input();int offset=Math.round((1-t)*16),color=args.get(4);args.set(1,(int)args.get(1)+offset);args.set(3,(int)args.get(3)+offset);args.set(4,((int)((color>>>24)*t)<<24)|(color&0xFFFFFF));}
    @Inject(method="removed",at=@At("HEAD"))
    private void aero$close(CallbackInfo ci) {
        if (!aero$opened) return;
        aero$opened = false;
        ChatMotion.close();
    }
}
