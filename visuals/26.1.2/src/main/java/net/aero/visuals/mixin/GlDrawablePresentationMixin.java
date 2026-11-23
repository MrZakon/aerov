package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class GlDrawablePresentationMixin {
    @WrapWithCondition(method = "presentFrame",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private boolean aero$presentVisibleDrawable(long window) {
        // Keep frame cleanup running while Windows has no visible swap surface.
        return Platform.get() != Platform.WINDOWS
                || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE;
    }
}
