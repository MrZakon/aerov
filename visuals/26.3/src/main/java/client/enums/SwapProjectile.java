package client.enums;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum SwapProjectile {
   SNOWBALL(Items.SNOWBALL, "Ком снега"),
   NETHER_STAR(Items.NETHER_STAR, "Стан"),
   SHULKER(null, "Рюкзак (любой шалкер)"),
   PRISMARINE_SHARD(Items.PRISMARINE_SHARD, "Взрывная трапка"),
   POPPED_CHORUS_FRUIT(Items.POPPED_CHORUS_FRUIT, "Трапка"),
   FIRE_CHARGE(Items.FIRE_CHARGE, "Взрывная штучка");

   public final Item item;
   public final String text;
   private static final Item[] itemArray = new Item[]{
      Items.SHULKER_BOX,
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.WHITE),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.ORANGE),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.MAGENTA),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.YELLOW),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.LIME),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.PINK),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.GRAY),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.CYAN),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.PURPLE),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.BLUE),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.BROWN),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.GREEN),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.RED),
      Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.BLACK)
   };
   private static final SwapProjectile[] swapProjectileArray = getSwapProjectileArray();

   private SwapProjectile(Item item2, String text2) {
      this.item = item2;
      this.text = text2;
   }

   public static Item[] getItemArray() {
      return itemArray;
   }

   private static SwapProjectile[] getSwapProjectileArray() {
      return new SwapProjectile[]{SNOWBALL, NETHER_STAR, SHULKER, PRISMARINE_SHARD, POPPED_CHORUS_FRUIT, FIRE_CHARGE};
   }

   public boolean check() {
      return this == SHULKER;
   }

   public static SwapProjectile getSwapProjectileByString(String text) {
      return Enum.valueOf(SwapProjectile.class, text);
   }
}
