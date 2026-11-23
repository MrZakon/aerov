package net.aero.visuals.feature.enhanced;

import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.component.DataComponentTypes;

/** Pulse ItemHighlighter reference, confined to already visible inventory slots. */
public final class ItemHighlighter extends VisualExpansion.Feature {
    private final BooleanSetting totems=flag("totems","Тотемы",true),apples=flag("apples","Золотые яблоки",true),pearls=flag("pearls","Эндер-жемчуг",true),potions=flag("potions","Зелья",true),tools=flag("tools","Оружие и инструменты",false),armor=flag("armor","Броня",false),enchanted=flag("enchanted","Зачарованные предметы",false),low=flag("low_durability","Изношенные предметы",true),pulse=flag("pulse","Пульсация",false);
    private final ColorSetting tint=color("color","Цвет выделения",0x709C85FF),warning=color("warning","Цвет низкой прочности",0xA0FF657B);
    public ItemHighlighter(){super("item_highlighter","ItemHighlighter","Цветовая подсветка выбранных типов предметов и низкой прочности в открытом инвентаре.",ModuleCategory.INVENTORY);}
    public void renderSlot(DrawContext d,Slot slot){if(!isEnabled()||slot==null||!slot.hasStack()||!slot.isEnabled())return;ItemStack s=slot.getStack();boolean worn=low.get()&&s.isDamageable()&&(s.getMaxDamage()-s.getDamage())/(double)Math.max(1,s.getMaxDamage())<.15;
        boolean selected=worn||totems.get()&&s.isOf(Items.TOTEM_OF_UNDYING)||apples.get()&&(s.isOf(Items.GOLDEN_APPLE)||s.isOf(Items.ENCHANTED_GOLDEN_APPLE))||pearls.get()&&s.isOf(Items.ENDER_PEARL)||potions.get()&&s.contains(DataComponentTypes.POTION_CONTENTS)||enchanted.get()&&s.hasEnchantments()||tools.get()&&(s.isIn(ItemTags.SWORDS)||s.isIn(ItemTags.AXES)||s.isIn(ItemTags.PICKAXES)||s.isIn(ItemTags.SHOVELS)||s.isIn(ItemTags.HOES))||armor.get()&&(s.isIn(ItemTags.HEAD_ARMOR)||s.isIn(ItemTags.CHEST_ARMOR)||s.isIn(ItemTags.LEG_ARMOR)||s.isIn(ItemTags.FOOT_ARMOR));
        if(!selected)return;int color=(worn?warning:tint).get().eval();if(pulse.get())color=ColorUtil.withAlpha(color,(int)((color>>>24)*(.7+.3*Math.sin(System.nanoTime()/3.0e8))));UiRenderer.roundedRect(d,slot.x,slot.y,16,16,3,color);UiRenderer.roundedBorder(d,slot.x,slot.y,16,16,3,.7,ColorUtil.withAlpha(color,Math.min(230,(color>>>24)+60)));
    }
}
