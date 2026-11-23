package client.data;

import client.util.UnsafeFields;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class ScreenSlots {
   private static UnsafeFields<Integer> unsafeFields;
   private static UnsafeFields<Integer> unsafeFields2;
   private static UnsafeFields<Integer> unsafeFields3;
   private static UnsafeFields<Integer> unsafeFields4;
   private static boolean flag = false;

   private ScreenSlots() {
   }

   public static int[] getIntArrayByHandledScreen(AbstractContainerScreen handledScreen) {
      if (handledScreen == null) {
         return new int[]{0, 0};
      } else {
         update();
         int i = unsafeFields.getIntByObject(handledScreen);
         int j = unsafeFields2.getIntByObject(handledScreen);
         if (i == 0 && j == 0) {
            int k = unsafeFields3.getIntByObject(handledScreen);
            int l = unsafeFields4.getIntByObject(handledScreen);
            if (k == 0) {
               k = 176;
            }

            if (l == 0) {
               l = 166;
            }

            return new int[]{(handledScreen.width - k) / 2, (handledScreen.height - l) / 2};
         } else {
            return new int[]{i, j};
         }
      }
   }

   private static void update() {
      if (!flag) {
         unsafeFields = new UnsafeFields<>(null, AbstractContainerScreen.class, 23);
         unsafeFields2 = new UnsafeFields<>(null, AbstractContainerScreen.class, 24);
         unsafeFields3 = new UnsafeFields<>(null, AbstractContainerScreen.class, 9);
         unsafeFields4 = new UnsafeFields<>(null, AbstractContainerScreen.class, 10);
         flag = true;
      }
   }
}
