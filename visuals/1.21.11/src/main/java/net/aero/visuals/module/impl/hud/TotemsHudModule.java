package net.aero.visuals.module.impl.hud;
import net.aero.visuals.hud.*;import net.minecraft.client.MinecraftClient;import net.minecraft.client.gui.DrawContext;import net.minecraft.item.*;
public final class TotemsHudModule extends HudWidgetModule {
    private int count;
    public TotemsHudModule(){super("totems_hud","Totems","Количество тотемов в своём инвентаре и второй руке.",new HudPosition(HudAnchor.BOTTOM_LEFT,10,75,1));}
    @Override protected void refresh(MinecraftClient c,float delta){count=0;for(int i=0;i<36;i++){var s=c.player.getInventory().getStack(i);if(s.isOf(Items.TOTEM_OF_UNDYING))count+=s.getCount();}var off=c.player.getOffHandStack();if(off.isOf(Items.TOTEM_OF_UNDYING))count+=off.getCount();content=count>0;width=105;height=51;}
    @Override protected void renderHud(DrawContext d,MinecraftClient c){title(d,"Тотемы");d.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING),9,27);text(d,"× "+count,32,31,11,0xFFF1F4FF);}
}
