package net.aero.visuals.alt;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;

final class MultiAccountFocus {
    static final int BACKGROUND_WIDTH = 854;
    static final int BACKGROUND_HEIGHT = 480;

    private MultiAccountFocus() {}

    static void hide(Minecraft client) {
        SDLVideo.SDL_HideWindow(client.getWindow().handle());
    }

    static int frameWidth(Minecraft client) { return client.getWindow().getWidth(); }
    static int frameHeight(Minecraft client) { return client.getWindow().getHeight(); }
    static int preferredFps(Minecraft client) { return client.options.framerateLimit().get(); }

    static void resize(Minecraft client, int width, int height) {
        SDLVideo.SDL_SetWindowSize(client.getWindow().handle(), width, height);
    }

    static void capturePointer(Minecraft client, boolean captured) {
        SDLMouse.SDL_SetWindowRelativeMouseMode(client.getWindow().handle(), captured);
    }
}
