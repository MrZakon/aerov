package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.systems.RenderSystem;
import net.aero.visuals.render.FullscreenFramePacer;
import net.aero.visuals.render.FullscreenPresentation;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class RenderSystemFramePacingMixin {

    // flipFrame polls the window before this call; use the freshly delivered focus/minimize state.
    @WrapWithCondition(method = "flipFrame", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static boolean aero$presentVisibleFullscreen(long handle) {
        return FullscreenPresentation.canPresent(MinecraftClient.getInstance());
    }

    @Inject(method="limitDisplayFPS",at=@At("HEAD"),cancellable=true)
    private static void aero$paceFullscreenFrames(int limit,CallbackInfo ci){
        var client=MinecraftClient.getInstance();
        if(Platform.get()!=Platform.WINDOWS||client==null||client.getWindow()==null||!client.getWindow().isFullscreen()){
            FullscreenFramePacer.reset();
            return;
        }
        FullscreenFramePacer.limit(limit,client.isWindowFocused(),client.getWindow().isMinimized());
        ci.cancel();
    }
}
