package net.aero.visuals.render;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;

/** Exclusive fullscreen can temporarily lose its drawable during an Alt+Tab. */
public final class FullscreenPresentation {
    private static boolean deferredFrame;

    private FullscreenPresentation() {}

    public static void resetFrame() {
        deferredFrame = false;
    }

    public static boolean canPresent(MinecraftClient client) {
        // A restore during the final event poll must not present a frame we didn't draw.
        if (!deferredFrame) deferredFrame = !hasDrawable(client);
        return !deferredFrame;
    }

    private static boolean hasDrawable(MinecraftClient client) {
        if (Platform.get() != Platform.WINDOWS || client == null) return true;
        var window = client.getWindow();
        if (window == null) return true;
        long handle = window.getHandle();
        if (handle == 0L || GLFW.glfwGetWindowMonitor(handle) == 0L) return true;

        // Query Win32 through GLFW before Minecraft has dispatched its window callbacks.
        return !window.hasZeroWidthOrHeight()
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }
}
