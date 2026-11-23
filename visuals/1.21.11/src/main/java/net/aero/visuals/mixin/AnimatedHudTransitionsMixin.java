package net.aero.visuals.mixin;
import net.aero.visuals.animation.*;
import net.minecraft.client.gui.hud.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class AnimatedHudTransitionsMixin {
    @Shadow @Final private PlayerListHud playerListHud;
    @Inject(method="renderPlayerList",at=@At("RETURN"))private void aero$tabClose(DrawContext d,RenderTickCounter counter,CallbackInfo ci){((AnimatedPlayerList)playerListHud).aero$closing(d);}
    @Inject(method="renderChat",at=@At("RETURN"))private void aero$chatClose(DrawContext d,RenderTickCounter counter,CallbackInfo ci){ChatMotion.drawClosing(d);}
}
