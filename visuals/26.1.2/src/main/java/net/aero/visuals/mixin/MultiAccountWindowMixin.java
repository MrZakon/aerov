package net.aero.visuals.mixin;

import com.mojang.blaze3d.platform.Window;
import net.aero.visuals.alt.MultiAccountWorker;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class MultiAccountWindowMixin {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private static void aero$hideBackgroundWindow(CallbackInfoReturnable<Long> callback) {
        if (MultiAccountWorker.requested()) GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    }
}
