package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountView;
import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public abstract class InactivityFpsLimiterMixin {
    @Shadow private int maxFps;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$forceFpsInGui(CallbackInfoReturnable<Integer> cir) {
        int backgroundFps = MultiAccountWorker.frameLimit();
        if (backgroundFps > 0) {
            cir.setReturnValue(backgroundFps);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        int viewFps = MultiAccountView.frameLimit(mc);
        if (viewFps > 0) {
            cir.setReturnValue(viewFps);
            return;
        }
        if (mc != null && mc.isWindowFocused() && mc.getWindow() != null && !mc.getWindow().isMinimized()
                && mc.currentScreen instanceof client.gui.screen.ClickGuiScreen) {
            cir.setReturnValue(this.maxFps > 0 ? this.maxFps : 260);
        }
    }

    @Inject(method = "getLimitReason", at = @At("HEAD"), cancellable = true)
    private void aerovisuals$noThrottleInGui(CallbackInfoReturnable<InactivityFpsLimiter.LimitReason> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int viewFps = MultiAccountView.frameLimit(mc);
        if (viewFps > 0) {
            cir.setReturnValue(InactivityFpsLimiter.LimitReason.NONE);
            return;
        }
        if (mc != null && mc.isWindowFocused() && mc.getWindow() != null && !mc.getWindow().isMinimized()
                && mc.currentScreen instanceof client.gui.screen.ClickGuiScreen) {
            cir.setReturnValue(InactivityFpsLimiter.LimitReason.NONE);
        }
    }
}
