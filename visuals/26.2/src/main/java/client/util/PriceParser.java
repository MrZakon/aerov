package client.util;

import client.module.Feature;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;

public final class PriceParser {
   private static final Pattern pattern = Pattern.compile("(\\d{1,3}(?:[\\s ,._]\\d{3})+|\\d+)");

   private PriceParser() {
   }

   private static long getLongByString(String text2) {
      long i = 0L;

      for (int j = 0; j < text2.length(); j++) {
         char c0 = text2.charAt(j);
         if (c0 >= '0' && c0 <= '9') {
            i = i * 10L + (c0 - '0');
         }
      }

      return i;
   }

   public static long getLongByItemStack(ItemStack itemStack) {
      ItemLore lorecomponent = (ItemLore)itemStack.get(DataComponents.LORE);
      if (lorecomponent == null) {
         return 0L;
      } else {
         for (Component text : lorecomponent.lines()) {
            if (text != null) {
               String s = ChatFormatting.stripFormatting(text.getString());
               if (s != null && !s.isEmpty()) {
                  String s1 = s.toLowerCase(Locale.ROOT);
                  if (!s1.contains("за 1 шт") && isString(s1)) {
                     long i = getLongByString2(s);
                     if (i > 0L) {
                        return i;
                     }
                  }
               }
            }
         }

         return 0L;
      }
   }

   public static String getStringByLong(long time) {
      StringBuilder stringbuilder = new StringBuilder();
      String s = String.valueOf(time);
      int i = 0;

      for (int j = s.length() - 1; j >= 0; j--) {
         if (i > 0 && i % 3 == 0) {
            stringbuilder.insert(0, '.');
         }

         stringbuilder.insert(0, s.charAt(j));
         i++;
      }

      return stringbuilder.toString();
   }

   public static boolean isStringArrayGenericContainerScreen(String[] textArray, ContainerScreen genericContainerScreen) {
      if (genericContainerScreen != null && genericContainerScreen.getTitle() != null) {
         String s = ChatFormatting.stripFormatting(genericContainerScreen.getTitle().getString());
         if (s == null) {
            return false;
         } else {
            String s1 = s.toLowerCase(Locale.ROOT);

            for (String s2 : textArray) {
               if (s1.contains(s2)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static int getIntByString(String text2) {
      if (text2 != null && !text2.isEmpty()) {
         int i = -1;
         Matcher matcher = pattern.matcher(text2);

         while (matcher.find()) {
            long j = getLongByString(matcher.group(1));
            if (j > 0L && j <= 2147483647L) {
               i = (int)j;
            }
         }

         return i;
      } else {
         return -1;
      }
   }

   public static boolean isString(String text2) {
      return !text2.contains("цена") && !text2.contains("price") && !text2.contains("стоимость") && !text2.contains("cost") && !text2.contains("¤")
         ? text2.matches(".*\\$\\s*[\\d\\s ,._]+.*")
         : true;
   }

   public static long getLongByString2(String text2) {
      long i = 0L;
      Matcher matcher = pattern.matcher(text2);

      while (matcher.find()) {
         long j = getLongByString(matcher.group(1));
         if (j > i) {
            i = j;
         }
      }

      return i;
   }

   public static int getIntByItemStack(ItemStack itemStack) {
      try {
         List list = itemStack.getTooltipLines(TooltipContext.EMPTY, Feature.mc.player, TooltipFlag.NORMAL);
         if (list != null && !list.isEmpty()) {
            for (int i = list.size() - 1; i >= 1; i--) {
               Component text = (Component)list.get(i);
               if (text != null) {
                  String s = ChatFormatting.stripFormatting(text.getString());
                  if (s != null && !s.isEmpty()) {
                     String s1 = s.toLowerCase(Locale.ROOT);
                     if (!s1.contains("за 1 шт") && isString(s1)) {
                        int j = getIntByString(s);
                        if (j > 0) {
                           return j;
                        }
                     }
                  }
               }
            }

            return -1;
         } else {
            return -1;
         }
      } catch (Throwable throwable) {
         return -1;
      }
   }
}
