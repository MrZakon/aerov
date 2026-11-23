package net.aero.visuals.module.impl.hud;
import net.aero.visuals.hud.*;import net.aero.visuals.render.UiRenderer;import net.minecraft.client.MinecraftClient;import net.minecraft.client.gui.DrawContext;
public final class SaturationHudModule extends HudWidgetModule {
    public SaturationHudModule(){super("saturation_hud","Saturation","Голод и насыщение своего персонажа по данным Minecraft.",new HudPosition(HudAnchor.BOTTOM_RIGHT,10,75,1));}
    @Override protected void refresh(MinecraftClient c,float delta){width=150;height=57;content=!c.player.isSpectator()&&!c.player.isCreative();}
    @Override protected void renderHud(DrawContext d,MinecraftClient c){title(d,"Насыщение");var food=c.player.getHungerManager();text(d,food.getFoodLevel()+" / 20  ·  "+String.format(java.util.Locale.ROOT,"%.1f",food.getSaturationLevel()),9,29,9,0xFFF1F4FF);UiRenderer.roundedRect(d,9,44,width-18,4,2,0x30FFFFFF);UiRenderer.roundedRect(d,9,44,(width-18)*Math.clamp(food.getFoodLevel()/20.0,0,1),4,2,0xFFDFAC70);UiRenderer.roundedRect(d,9,51,(width-18)*Math.clamp(food.getSaturationLevel()/20.0,0,1),2,1,0xFF83D8A7);}
}
