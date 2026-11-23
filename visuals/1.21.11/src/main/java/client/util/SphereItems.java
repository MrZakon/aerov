package client.util;

import client.enums.TrackedItem;
import client.module.Feature;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public final class SphereItems extends Feature {
   private SphereItems() {
   }

   public static ItemStack getItemStackByTrackedItem(TrackedItem trackedItem) {
      if (trackedItem != null && trackedItem.item != null) {
         ItemStack itemstack = new ItemStack(trackedItem.item);
         if (trackedItem.value >= 0) {
            itemstack.set(
               DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(trackedItem.value), List.of(), Optional.empty())
            );
         }

         return itemstack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static void onConsumer(Consumer<ItemStack> consumer) {
      if (Feature.mc.player != null && consumer != null) {
         for (Slot slot : Feature.mc.player.currentScreenHandler.slots) {
            if (slot.id >= 9 && slot.id <= 44) {
               ItemStack itemstack = slot.getStack();
               if (itemstack != null && !itemstack.isEmpty()) {
                  BundleContentsComponent bundlecontentscomponent = (BundleContentsComponent)itemstack.get(DataComponentTypes.BUNDLE_CONTENTS);
                  if (bundlecontentscomponent != null && !bundlecontentscomponent.isEmpty()) {
                     for (int i = 0; i < bundlecontentscomponent.size(); i++) {
                        ItemStack itemstack1 = bundlecontentscomponent.get(i);
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
         CustomModelDataComponent custommodeldatacomponent = (CustomModelDataComponent)itemStack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
         return custommodeldatacomponent != null && !custommodeldatacomponent.floats().isEmpty()
            ? (int)custommodeldatacomponent.floats().getFirst().floatValue() == count
            : false;
      } else {
         return false;
      }
   }

   public static boolean isItemStackInt2(ItemStack itemStack, int count) {
      if (itemStack != null && !itemStack.isEmpty() && itemStack.getItem() == Items.SPLASH_POTION) {
         PotionContentsComponent potioncontentscomponent = (PotionContentsComponent)itemStack.get(DataComponentTypes.POTION_CONTENTS);
         return potioncontentscomponent != null
            && potioncontentscomponent.customColor().isPresent()
            && potioncontentscomponent.customColor().get() == count;
      } else {
         return false;
      }
   }

   public static boolean isStringItemStack(String text2, ItemStack itemStack) {
      if (itemStack != null && !itemStack.isEmpty() && text2 != null) {
         LoreComponent lorecomponent = (LoreComponent)itemStack.get(DataComponentTypes.LORE);
         if (lorecomponent == null) {
            return false;
         } else {
            for (Text text : lorecomponent.lines()) {
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
      return itemStack != null && !itemStack.isEmpty() ? isStringString(itemStack.getName().getString(), text2) : false;
   }

   private static boolean isStringString(String s1, String s2) {
      if (s1 == null || s2 == null) {
         return false;
      } else {
         return s1.toLowerCase(Locale.ROOT).contains(s2.toLowerCase(Locale.ROOT));
      }
   }
}
