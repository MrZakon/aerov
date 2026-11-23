package client.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

public final class ItemIconCache {
   private static DrawContext currentDrawContext;

   private ItemIconCache() {}

   public static void setDrawContext(DrawContext drawContext) {
      currentDrawContext = drawContext;
   }

   public static DrawContext getDrawContext() {
      return currentDrawContext;
   }

   public static void update() {}
   public static void update3() {}
   public static void update4() {}
   public static void setFloat(float value) {}
   public static void onFloatDrawContext(float value, DrawContext drawContext) {}
   public static void render(DrawContext drawContext) {}

   public static void onFloatFloatFloatItemStackMatrix4fFloat(float size, float alpha, float x, ItemStack itemStack, Matrix4f matrix4f, float y) {
      onFloatItemStackFloatFloatFloatDrawContext(size, itemStack, alpha, y, x, currentDrawContext);
   }

   public static void onFloatFloatItemStackMatrix4fFloat(float x, float size, ItemStack itemStack, Matrix4f matrix4f, float y) {
      onFloatFloatFloatItemStackMatrix4fFloat(size, 1.0F, x, itemStack, matrix4f, y);
   }

   public static void onFloatItemStackFloatFloatFloatDrawContext(float size, ItemStack itemStack, float alpha, float y, float x, DrawContext drawContext) {
      DrawContext ctx = drawContext != null ? drawContext : currentDrawContext;
      if (ctx == null || itemStack == null || itemStack.isEmpty() || alpha <= 0.001F || size <= 0.0F) return;
      if (size == 16.0F) {
         ctx.drawItem(itemStack, Math.round(x), Math.round(y));
      } else {
         Matrix3x2fStack matrices = ctx.getMatrices();
         if (matrices != null) {
            matrices.pushMatrix();
            try {
               matrices.translate(x, y);
               float s = size / 16.0F;
               matrices.scale(s, s);
               ctx.drawItem(itemStack, 0, 0);
            } finally {
               matrices.popMatrix();
            }
         } else {
            ctx.drawItem(itemStack, Math.round(x), Math.round(y));
         }
      }
   }

   public static void onFloatDrawContextFloatItemStackFloat(float alpha, DrawContext drawContext, float x, ItemStack itemStack, float y) {
      onFloatItemStackFloatFloatFloatDrawContext(16.0F, itemStack, alpha, y, x, drawContext);
   }
}
