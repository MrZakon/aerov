package net.aero.visuals.mixin;

import net.aero.visuals.alt.MultiAccountWorker;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Window.class)
public abstract class MultiAccountWindowMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private long aero$createBackgroundWindow(int width, int height, CharSequence title, long monitor, long sharedContext) {
        if (MultiAccountWorker.requested()) GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        return GLFW.glfwCreateWindow(width, height, title, monitor, sharedContext);
    }
}
