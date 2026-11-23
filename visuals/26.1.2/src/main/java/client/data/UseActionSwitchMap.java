package client.data;

import net.minecraft.world.item.ItemUseAnimation;

public class UseActionSwitchMap {
   public static final int[] intArray = new int[ItemUseAnimation.values().length];

   static {
      try {
         intArray[ItemUseAnimation.NONE.ordinal()] = 1;
      } catch (NoSuchFieldError nosuchfielderror7) {
      }

      try {
         intArray[ItemUseAnimation.EAT.ordinal()] = 2;
      } catch (NoSuchFieldError nosuchfielderror6) {
      }

      try {
         intArray[ItemUseAnimation.DRINK.ordinal()] = 3;
      } catch (NoSuchFieldError nosuchfielderror5) {
      }

      try {
         intArray[ItemUseAnimation.BLOCK.ordinal()] = 4;
      } catch (NoSuchFieldError nosuchfielderror4) {
      }

      try {
         intArray[ItemUseAnimation.BOW.ordinal()] = 5;
      } catch (NoSuchFieldError nosuchfielderror3) {
      }

      try {
         intArray[ItemUseAnimation.SPEAR.ordinal()] = 6;
      } catch (NoSuchFieldError nosuchfielderror2) {
      }

      try {
         intArray[ItemUseAnimation.BRUSH.ordinal()] = 7;
      } catch (NoSuchFieldError nosuchfielderror1) {
      }

      try {
         intArray[ItemUseAnimation.BUNDLE.ordinal()] = 8;
      } catch (NoSuchFieldError nosuchfielderror) {
      }
   }
}
