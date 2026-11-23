package net.aero.visuals.mixin;

import com.mojang.blaze3d.platform.Window;
import net.aero.visuals.alt.MultiAccountWorker;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;


@Mixin(Window.class)
public abstract class MultiAccountWindowMixin {
    @ModifyArg(method = "createWindow", at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3)
    private long aero$hideBackgroundWindow(long flags) {
        return MultiAccountWorker.requested() ? flags | SDLVideo.SDL_WINDOW_HIDDEN : flags;
    }
}
