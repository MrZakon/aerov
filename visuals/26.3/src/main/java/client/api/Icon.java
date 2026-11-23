package client.api;

import client.module.CategoryType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

public interface Icon {
   public static Icon getIconByCategoryType(CategoryType categoryType) {
      return new CategoryIcon(categoryType);
   }

   public static Icon getIconByRegistryEntry(Holder registryEntry) {
      return new CategoryIcon(CategoryType.INFO);
   }

   public static Icon getIconByItem(Item item2) {
      return new ItemStackIcon(new net.minecraft.world.item.ItemStackTemplate(item2));
   }

   public static Icon getIconByItemStack(ItemStack itemStack) {
      return itemStack != null && !itemStack.isEmpty() ? new ItemStackIcon(itemStack) : getIconByItem(Items.PAPER);
   }

   public void draw(float value, float value2, float value3, Matrix4f matrix4f, float value4, GuiGraphicsExtractor drawContext);
}
