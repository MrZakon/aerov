package net.aero.visuals.module.impl.hud;
import net.aero.visuals.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
public final class TotemsHudModule extends HudWidgetModule {
    private int count;
    public TotemsHudModule(){super("totems_hud","Totems","Количество тотемов в своём инвентаре и второй руке.",new HudPosition(HudAnchor.BOTTOM_LEFT,10,75,1));}
    @Override protected void refresh(Minecraft c,float delta){count=0;for(int i=0;i<36;i++){var s=c.player.getInventory().getItem(i);if(s.is(Items.TOTEM_OF_UNDYING))count+=s.getCount();}var off=c.player.getOffhandItem();if(off.is(Items.TOTEM_OF_UNDYING))count+=off.getCount();content=count>0;width=105;height=51;}
    @Override protected void renderHud(GuiGraphicsExtractor d,Minecraft c){title(d,"Тотемы");d.item(new ItemStack(Items.TOTEM_OF_UNDYING),9,27);text(d,"× "+count,32,31,11,0xFFF1F4FF);}
}
