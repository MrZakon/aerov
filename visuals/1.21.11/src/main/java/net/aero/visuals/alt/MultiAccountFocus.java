package net.aero.visuals.alt;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

final class MultiAccountFocus {
    static final int BACKGROUND_WIDTH = 854;
    static final int BACKGROUND_HEIGHT = 480;

    private MultiAccountFocus() {}

    static void hide(MinecraftClient client) {
        GLFW.glfwHideWindow(client.getWindow().getHandle());
    }

    static int frameWidth(MinecraftClient client) { return client.getWindow().getFramebufferWidth(); }
    static int frameHeight(MinecraftClient client) { return client.getWindow().getFramebufferHeight(); }
    static int preferredFps(MinecraftClient client) { return client.options.getMaxFps().getValue(); }

    static void resize(MinecraftClient client, int width, int height) {
        GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height);
    }

    static void capturePointer(MinecraftClient client, boolean captured) {
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR,
                captured ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
    }
}
