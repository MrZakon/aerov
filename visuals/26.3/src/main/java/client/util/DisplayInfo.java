package client.util;
import net.minecraft.client.Minecraft;
public final class DisplayInfo {
    private DisplayInfo(){}
    public static int getInt(){var mc=Minecraft.getInstance();if(mc==null||mc.getWindow()==null)return 60;var mode=mc.getWindow().getActiveVideoMode();return mode==null?60:Math.max(1,Math.round(mode.getRefreshRate()));}
}