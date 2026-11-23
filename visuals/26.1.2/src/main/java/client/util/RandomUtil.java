package client.util;

import client.module.Feature;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class RandomUtil {
   private static final RandomSource random = RandomSource.create();

   public static double getDouble() {
      double d0 = (Double)Feature.mc.options.sensitivity().get();
      double d1 = d0 * 0.6 + 0.2;
      return d1 * d1 * d1 * 1.2;
   }

   public static double getDoubleByDouble(double value) {
      value %= 360.0;
      if (value > 180.0) {
         value -= 360.0;
      }

      if (value < -180.0) {
         value += 360.0;
      }

      return value;
   }

   public static double getDoubleByDoubleDouble(double value, double value2) {
      return value <= 1.0E-4 ? value2 : value2 - value2 % value;
   }

   public static boolean isIntIntDouble(int count, int count2, double value) {
      return Mth.nextDouble(random, count, count2 + 1) > value;
   }

   public static float getFloatByFloatFloatFloat(float value, float value2, float value3) {
      return Math.max(value, Math.min(value2, value3));
   }
}
