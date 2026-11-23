package client.util;

import java.util.Arrays;

public class TpsTracker {
   private static final TpsTracker INSTANCE = new TpsTracker();
   private static final float[] floatArray = new float[20];
   private static int value = 0;
   private static long time = -1L;

   static {
      Arrays.fill(floatArray, 20.0F);
   }

   public static TpsTracker getInstance() {
      return INSTANCE;
   }

   public static void update() {
      Arrays.fill(floatArray, 20.0F);
      value = 0;
      time = -1L;
   }

   public static float getFloat() {
      float f = 0.0F;
      float f1 = 0.0F;
      for (float f2 : floatArray) {
         if (f2 > 0.0F) {
            f1 += Math.clamp(f2, 0.0F, 20.0F);
            f++;
         }
      }
      return f == 0.0F ? 20.0F : Math.clamp(f1 / f, 0.0F, 20.0F);
   }
}
