package net.aero.visuals.render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.aero.visuals.mixin.GuiHudAccessor;
public final class ClientView {
    private ClientView(){}
    public static Hud hud(){return ((GuiHudAccessor)Minecraft.getInstance().gui).aero$hud();}
}
