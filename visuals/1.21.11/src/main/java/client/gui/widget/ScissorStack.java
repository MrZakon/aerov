package client.gui.widget;

import client.data.RectI;
import client.gui.screen.ClickGuiScreen;
import client.module.Feature;
import client.render.ItemIconCache;
import client.render.TextShader;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.DrawContext;

public final class ScissorStack {
   private static final Deque<RectI> deque = new ArrayDeque<>();
   private static float value;
   private static float value2;
   private static float value3 = 1.0F;
   private static float value4;
   private static float value5;
   private static boolean flag;

   private ScissorStack() {
   }

   public static void onFloatFloatFloatFloat(float value6, float value7, float value8, float value9) {
      if (flag) {
         float f = value3;
         float f1 = (value9 - value) * f + value + value4;
         float f2 = (value8 - value2) * f + value2 + value5;
         value9 = f1;
         value8 = f2;
         value6 *= f;
         value7 *= f;
      }

      int x1 = (int)Math.floor(value9);
      int y1 = (int)Math.floor(value8);
      int x2 = (int)Math.ceil(value9 + value6);
      int y2 = (int)Math.ceil(value8 + value7);
      if (!deque.isEmpty()) {
         RectI parent = deque.peek();
         int px1 = parent.getValue();
         int py1 = parent.getValue2();
         int px2 = px1 + parent.getValue3();
         int py2 = py1 + parent.getValue4();
         x1 = Math.max(x1, px1);
         y1 = Math.max(y1, py1);
         x2 = Math.max(x1, Math.min(x2, px2));
         y2 = Math.max(y1, Math.min(y2, py2));
      }
      RectI recti = new RectI(x1, y1, x2 - x1, y2 - y1);
      TextShader.update2();
      deque.push(recti);
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx != null) {
         ctx.enableScissor(x1, y1, x2, y2);
      }
   }

   public static void update() {
      if (!deque.isEmpty()) {
         TextShader.update2();
         deque.pop();
         DrawContext ctx = ItemIconCache.getDrawContext();
         if (ctx != null) {
            // DrawContext maintains its own stack: one pop restores its parent.
            // Pushing the parent again leaves the dropdown's clip active.
            ctx.disableScissor();
         }
      }
   }

   public static void update2() {
      flag = false;
      value3 = 1.0F;
      value4 = 0.0F;
      value5 = 0.0F;
   }

   public static void onFloatFloatFloatFloatFloat(float value6, float value7, float value8, float value9, float value10) {
      value = value10;
      value2 = value8;
      value3 = value9;
      value4 = value7;
      value5 = value6;
      flag = true;
   }

   public static void onFloatFloatFloatFloat2(float value, float value2, float value3, float value4) {
      float f = 0.0F;
      onFloatFloatFloatFloatFloat(value2, f, value4, value3, value);
   }
}
