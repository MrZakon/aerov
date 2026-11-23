package net.aero.visuals.render;
import java.net.URI;
import java.io.File;
import org.lwjgl.sdl.SDLMisc;
public final class PlatformLinks {
    private PlatformLinks(){}
    public static void openUri(String uri){openUri(URI.create(uri));}
    public static void openUri(URI uri){if(!SDLMisc.SDL_OpenURL(uri.toASCIIString()))net.aero.visuals.AeroVisuals.LOGGER.warn("Could not open external link");}
    public static void openFile(File file){openUri(file.toURI());}
}