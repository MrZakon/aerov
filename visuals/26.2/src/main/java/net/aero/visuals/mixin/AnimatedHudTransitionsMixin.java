package net.aero.visuals.mixin;
import net.aero.visuals.animation.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Hud.class)
public abstract class AnimatedHudTransitionsMixin {
    @Shadow @Final private PlayerTabOverlay tabList;
    @Inject(method="extractTabList",at=@At("RETURN"))private void aero$tabClose(GuiGraphicsExtractor d,DeltaTracker counter,CallbackInfo ci){((AnimatedPlayerList)tabList).aero$closing(d);}
    @Inject(method="extractChat",at=@At("RETURN"))private void aero$chatClose(GuiGraphicsExtractor d,DeltaTracker counter,CallbackInfo ci){ChatMotion.drawClosing(d);}
}
