package client.util;

import client.enums.TrackedItem;
import client.module.Feature;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

public final class SphereItems extends Feature {
   private SphereItems() {
   }

   public static ItemStack getItemStackByTrackedItem(TrackedItem trackedItem) {
      if (trackedItem != null && trackedItem.item != null) {
         ItemStack itemstack = new ItemStack(trackedItem.item);
         if (trackedItem.value >= 0) {
            itemstack.set(
               DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(trackedItem.value), List.of(), Optional.empty())
            );
         }

         return itemstack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static void onConsumer(Consumer<ItemStack> consumer) {
      if (Feature.mc.player != null && consumer != null) {
         for (Slot slot : Feature.mc.player.containerMenu.slots) {
            if (slot.index >= 9 && slot.index <= 44) {
               ItemStack itemstack = slot.getItem();
               if (itemstack != null && !itemstack.isEmpty()) {
                  BundleContents bundlecontentscomponent = (BundleContents)itemstack.get(DataComponents.BUNDLE_CONTENTS);
                  if (bundlecontentscomponent != null && !bundlecontentscomponent.isEmpty()) {
                     for (int i = 0; i < bundlecontentscomponent.size(); i++) {
                        ItemStack itemstack1 = bundlecontentscomponent.items().get(i).create();
                        if (itemstack1 != null && !itemstack1.isEmpty()) {
                           consumer.accept(itemstack1);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean isItemStackInt(ItemStack itemStack, int count) {
      if (itemStack != null && !itemStack.isEmpty() && itemStack.getItem() == Items.TOTEM_OF_UNDYING) {
         CustomModelData custommodeldatacomponent = (CustomModelData)itemStack.get(DataComponents.CUSTOM_MODEL_DATA);
         return custommodeldatacomponent != null && !custommodeldatacomponent.floats().isEmpty()
            ? (int)custommodeldatacomponent.floats().getFirst().floatValue() == count
            : false;
      } else {
         return false;
      }
   }

   public static boolean isItemStackInt2(ItemStack itemStack, int count) {
      if (itemStack != null && !itemStack.isEmpty() && itemStack.getItem() == Items.SPLASH_POTION) {
         PotionContents potioncontentscomponent = (PotionContents)itemStack.get(DataComponents.POTION_CONTENTS);
         return potioncontentscomponent != null
            && potioncontentscomponent.customColor().isPresent()
            && potioncontentscomponent.customColor().get() == count;
      } else {
         return false;
      }
   }

   public static boolean isStringItemStack(String text2, ItemStack itemStack) {
      if (itemStack != null && !itemStack.isEmpty() && text2 != null) {
         ItemLore lorecomponent = (ItemLore)itemStack.get(DataComponents.LORE);
         if (lorecomponent == null) {
            return false;
         } else {
            for (Component text : lorecomponent.lines()) {
               if (isStringString(text.getString(), text2)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isItemStackString(ItemStack itemStack, String text2) {
      return itemStack != null && !itemStack.isEmpty() ? isStringString(itemStack.getHoverName().getString(), text2) : false;
   }

   private static boolean isStringString(String s1, String s2) {
      if (s1 == null || s2 == null) {
         return false;
      } else {
         return s1.toLowerCase(Locale.ROOT).contains(s2.toLowerCase(Locale.ROOT));
      }
   }
}
