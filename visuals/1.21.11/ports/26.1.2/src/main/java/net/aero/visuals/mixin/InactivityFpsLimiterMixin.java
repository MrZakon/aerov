package net.aero.visuals.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class InactivityFpsLimiterMixin {
    @Shadow private int framerateLimit;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$forceFpsInGui(CallbackInfoReturnable<Integer> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.isWindowActive() && mc.getWindow() != null && !mc.getWindow().isIconified()
                && mc.screen instanceof client.gui.screen.ClickGuiScreen) {
            cir.setReturnValue(this.framerateLimit > 0 ? this.framerateLimit : 260);
        }
    }

    @Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$noThrottleInGui(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.isWindowActive() && mc.getWindow() != null && !mc.getWindow().isIconified()
                && mc.screen instanceof client.gui.screen.ClickGuiScreen) {
            cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
        }
    }
}
