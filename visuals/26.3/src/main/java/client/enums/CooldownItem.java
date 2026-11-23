package client.enums;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public enum CooldownItem {
   ENCHANTED_GOLDEN_APPLE("Чарка", 150, Items.ENCHANTED_GOLDEN_APPLE),
   NAUSEA("Дезка", 60, Items.ENDER_EYE),
   TOTEM("Тотем", 60, Items.TOTEM_OF_UNDYING),
   GOLDEN_APPLE("Гэпл", 30, Items.GOLDEN_APPLE),
   DRIED_KELP("Пласт", 30, Items.DRIED_KELP),
   HEALING_POTION("Исцеление", 20, Items.POTION),
   CHORUS_FRUIT("Хорус", 20, Items.CHORUS_FRUIT),
   NETHERITE_SCRAP("Трапка", 10, Items.NETHERITE_SCRAP);

   public final String text;
   public final long time;
   public final net.minecraft.world.item.ItemStackTemplate itemStack;
   private static final CooldownItem[] cooldownItemArray = getCooldownItemArray();

   private CooldownItem(String text2, int count, Item item2) {
      this.text = text2;
      this.time = count * 1000L;
      this.itemStack = item2 == Items.POTION
         ? new net.minecraft.world.item.ItemStackTemplate(item2, net.minecraft.core.component.DataComponentPatch.builder()
             .set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING)).build())
         : new net.minecraft.world.item.ItemStackTemplate(item2);
   }

   private static CooldownItem[] getCooldownItemArray() {
      return new CooldownItem[]{ENCHANTED_GOLDEN_APPLE, NAUSEA, TOTEM, GOLDEN_APPLE, DRIED_KELP, HEALING_POTION, CHORUS_FRUIT, NETHERITE_SCRAP};
   }

   public static CooldownItem getCooldownItemByString(String text) {
      return Enum.valueOf(CooldownItem.class, text);
   }
}
