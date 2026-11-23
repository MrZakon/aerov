package net.aero.visuals.gui;
import net.aero.visuals.AeroVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
public final class AeroBranding {
    private static final Identifier WINDOW_ICON=Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID,"textures/gui/logo.png");
    private AeroBranding(){}
    public static boolean applyWindowIcon(Minecraft client){
        if(client.getWindow()==null||client.getWindow().handle()==0)return false;
        try(var stream=client.getResourceManager().getResource(WINDOW_ICON).orElseThrow().open();var image=NativeImage.read(stream)){
            var surface=SDLSurface.SDL_CreateSurfaceFrom(image.getWidth(),image.getHeight(),376840196,image.getPixelBytes(),image.getWidth()*4);
            if(surface==null)throw new IllegalStateException("SDL icon surface unavailable");
            try{if(!SDLVideo.SDL_SetWindowIcon(client.getWindow().handle(),surface))throw new IllegalStateException("SDL rejected window icon");}finally{SDLSurface.SDL_DestroySurface(surface);}
        }catch(Exception error){AeroVisuals.LOGGER.warn("Could not apply the AeroVisuals window icon",error);}
        return true;
    }
}