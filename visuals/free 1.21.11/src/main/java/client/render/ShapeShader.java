package client.render;

import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

public class ShapeShader {
   public static final int value = 0;

   public static void update() {}
   public static void update2() {}

   public static int getIntByIntIntIntInt(int r, int g, int b, int a) {
      return (getIntByInt(a) & 0xFF) << 24 | (getIntByInt(r) & 0xFF) << 16 | (getIntByInt(g) & 0xFF) << 8 | (getIntByInt(b) & 0xFF);
   }

   public static int getIntByIntIntFloatInt(int r, int g, float a, int b) {
      int alpha = Math.round(Math.clamp(a, 0.0F, 1.0F) * 255.0F);
      return getIntByIntIntIntInt(r, g, b, alpha);
   }

   public static int getIntByIntFloat(int count, float value) {
      int a = (count >>> 24);
      if (a == 0) a = 255;
      int i = Math.round(a * Math.clamp(value, 0.0F, 1.0F));
      return (count & 0x00FFFFFF) | (Math.clamp(i, 0, 255) << 24);
   }

   private static int getIntByInt(int count) {
      return Math.clamp(count, 0, 255);
   }

   private static int applyAlpha(int color, float alpha) {
      if (alpha <= 0.0F) return 0;
      int a = (color >>> 24);
      if (a == 0) a = 255;
      int finalA = Math.clamp(Math.round(a * Math.clamp(alpha, 0.0F, 1.0F)), 0, 255);
      return (color & 0x00FFFFFF) | (finalA << 24);
   }

   public static void onFloatIntFloatFloatFloatFloatFloatIntMatrix4f(
      float radius, int bottomColor, float alpha, float y, float height, float width, float x, int topColor, Matrix4f matrix4f
   ) {
   }

   public static void onIntMatrix4fFloatIntFloatFloatFloat(int topColor, Matrix4f matrix4f, float width, int bottomColor, float height, float x, float y) {
   }

   public static void onIntFloatFloatIntFloatFloatMatrix4fFloatFloat(
      int leftColor, float height, float radius, int rightColor, float y, float x, Matrix4f matrix4f, float width, float alpha
   ) {
      if (width <= 0.0F || height <= 0.0F) return;
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      int lc = applyAlpha(leftColor, alpha);
      int rc = applyAlpha(rightColor, alpha);
      UiRenderer.horizontalGradient(ctx, x, y, width, height, lc, rc);
   }

   public static void onFloatIntIntFloatFloatMatrix4fIntIntFloat(
      float value, int count, int count2, float value2, float value3, Matrix4f matrix4f, int count3, int count4, float value4
   ) {
   }

   public static void onFloatIntFloatFloatFloatIntMatrix4fFloatIntIntFloat(
      float y, int colorTL, float alpha, float radius, float width, int colorBL, Matrix4f matrix4f, float x, int colorBR, int colorTR, float height
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      int c = applyAlpha(colorTL, alpha);
      UiRenderer.roundedRect(ctx, x, y, width, height, radius > 0.0F ? radius : 8.0, c);
   }

   // Ring / Circle
   public static void onIntFloatMatrix4fFloatFloatFloatFloatFloat(
      int count, float value, Matrix4f matrix4f, float value3, float value4, float value5, float value6, float value7
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      int c = applyAlpha(count, value6);
      float r = value7 * 0.5F;
      float thick = Math.max(1.0F, Math.min(value4, r));
      UiRenderer.roundedBorder(ctx, value5, value, value7, value7, r, thick, c);
   }

   // Hue strip
   public static void onFloatFloatMatrix4fFloatFloatFloatFloat(float value, float value3, Matrix4f matrix4f, float value4, float value5, float value6, float value7) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float x = value5;
      float y = value3;
      float width = value6;
      float height = value;
      float alpha = value7;
      int segments = 6;
      float segW = width / segments;
      int[] hueColors = { 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000 };
      for (int i = 0; i < segments; i++) {
         UiRenderer.horizontalGradient(ctx, x + i * segW, y, segW + 0.5f, height, applyAlpha(hueColors[i], alpha), applyAlpha(hueColors[i + 1], alpha));
      }
   }

   // 2D SV Color Box
   public static void onFloatMatrix4fFloatFloatFloatFloatFloatInt(
      float value, Matrix4f matrix4f, float value3, float value4, float value5, float value6, float value7, int count
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float x = value5;
      float y = value7;
      float width = value6;
      float height = value3;
      float alpha = value;
      int white = applyAlpha(0xFFFFFFFF, alpha);
      int hue = applyAlpha(count | 0xFF000000, alpha);
      UiRenderer.horizontalGradient(ctx, x, y, width, height, white, hue);
      int blackTop = applyAlpha(0x00000000, alpha);
      int blackBottom = applyAlpha(0xFF000000, alpha);
      UiRenderer.verticalGradient(ctx, x, y, width, height, blackTop, blackBottom);
   }

   // Rounded rect
   public static void onFloatFloatIntMatrix4fFloatFloatFloatFloat(
      float value, float value2, int count, Matrix4f matrix4f, float value3, float value4, float value5, float value6
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float radius = value;
      float x = value2;
      float height = value3;
      float width = value4;
      float alpha = value5;
      float y = value6;
      int c = applyAlpha(count, alpha);
      UiRenderer.roundedRect(ctx, x, y, width, height, radius, c);
   }

   public static void onFloatIntIntFloatFloatFloatFloatFloatFloatMatrix4fFloatIntFloatFloatFloatFloatFloat(
      float value, int count, int count2, float value2, float value3, float value4, float value5, float value6,
      float value7, Matrix4f matrix4f, float value8, int count3, float value9, float value10, float value11,
      float value12, float value13
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float x = value2;
      float y = value3;
      float width = value;
      float height = value6;
      float radius = Math.max(Math.max(value5, value7), Math.max(value9, value12));
      float alpha = value8;
      int c = applyAlpha(count2, alpha);
      int bc = applyAlpha(count3, alpha);
      float thick = Math.max(0.0F, value10);
      UiRenderer.roundedRect(ctx, x, y, width, height, radius, c);
      if (thick > 0.0F && (bc >>> 24) > 0) {
         UiRenderer.roundedBorder(ctx, x, y, width, height, radius, thick, bc);
      }
   }

   public static void onFloatFloatFloatIntFloatArrayFloatFloatArrayFloatMatrix4fFloatFloatFloatFloatFloatFloatIntFloatFloatIntFloat(
      float borderWidth,
      float x,
      float y,
      int shadowColor,
      float[] segHalfWidths,
      float radiusTL,
      float[] segCenters,
      float radiusTR,
      Matrix4f matrix4f,
      float radiusBR,
      float shadowOffX,
      float shadowOffY,
      float shadowBlur,
      float radiusBL,
      float width,
      int fillColor,
      float alpha,
      float height,
      int borderColor,
      float segRadius
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      int c = applyAlpha(fillColor, alpha);
      int bc = applyAlpha(borderColor, alpha);
      float thick = Math.max(0.0F, borderWidth);
      if (segCenters != null && segHalfWidths != null && segCenters.length > 0) {
         int n = Math.min(segCenters.length, segHalfWidths.length);
         float r = segRadius > 0.0F ? segRadius : radiusTL;
         for (int i = 0; i < n; i++) {
            float sx = x + segCenters[i] - segHalfWidths[i];
            float sw = segHalfWidths[i] * 2.0F;
            if (sw > 0.0F) {
               UiRenderer.roundedRect(ctx, sx, y, sw, height, r, c);
               if (thick > 0.0F && (bc >>> 24) > 0) {
                  UiRenderer.roundedBorder(ctx, sx, y, sw, height, r, thick, bc);
               }
            }
         }
      } else {
         float r = Math.max(Math.max(radiusTL, radiusTR), Math.max(radiusBR, radiusBL));
         UiRenderer.roundedRect(ctx, x, y, width, height, r, c);
         if (thick > 0.0F && (bc >>> 24) > 0) {
            UiRenderer.roundedBorder(ctx, x, y, width, height, r, thick, bc);
         }
      }
   }

   public static void onFloatFloatIntIntMatrix4fFloatFloat(float height, float x, int count, int count2, Matrix4f matrix4f, float y, float width) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      UiRenderer.roundedRect(ctx, x, y, width, height, 6.0, applyAlpha(count, 1.0F));
   }

   // Rect without corner radius
   public static void onFloatFloatFloatFloatFloatMatrix4fInt(float value, float value2, float value3, float value4, float value5, Matrix4f matrix4f, int count) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float alpha = value;
      float height = value2;
      float x = value3;
      float y = value4;
      float width = value5;
      int c = applyAlpha(count, alpha);
      UiRenderer.rect(ctx, x, y, width, height, c);
   }

   public static void onFloatFloatMatrix4fFloatFloatIntFloatFloat(
      float height, float x, Matrix4f matrix4f, float width, float radius, int count, float y, float alpha
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      int c = applyAlpha(count, alpha);
      UiRenderer.roundedRect(ctx, x, y, width, height, radius, c);
   }

   // Main container rounded rect with border
   public static void onFloatFloatFloatMatrix4fFloatIntFloatFloatIntFloat(
      float value, float value2, float value3, Matrix4f matrix4f, float value4, int count, float value5, float value6, int count2, float value7
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float borderThickness = value;
      float width = value2;
      float alpha = value3;
      float y = value4;
      int color = count;
      float radius = value5;
      float x = value6;
      int borderColor = count2;
      float height = value7;

      int c = applyAlpha(color, alpha);
      int bc = applyAlpha(borderColor, alpha);
      if ((c >>> 24) > 0) {
         UiRenderer.roundedRect(ctx, x, y, width, height, radius, c);
      }
      if (borderThickness > 0.0F && (bc >>> 24) > 0) {
         UiRenderer.roundedBorder(ctx, x, y, width, height, radius, borderThickness, bc);
      }
   }

   public static void onFloatArrayFloatFloatFloatIntFloatArrayFloatFloatFloatFloatFloatIntFloatMatrix4fFloatIntFloatFloatArrayFloatArray(
      float[] valueArray, float width, float alpha, float borderWidth, int borderColor, float[] valueArray2, float y, float value5,
      float value6, float height, float value8, int shadowColor, float value9, Matrix4f matrix4f, float x, int fillColor,
      float value11, float[] valueArray3, float[] valueArray4
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float r = (valueArray4 != null && valueArray4.length > 0) ? valueArray4[0] : 6.0F;
      int c = applyAlpha(fillColor, alpha);
      int bc = applyAlpha(borderColor, alpha);
      float thick = Math.max(0.0F, borderWidth);
      UiRenderer.roundedRect(ctx, x, y, width, height, r, c);
      if (thick > 0.0F && (bc >>> 24) > 0) {
         UiRenderer.roundedBorder(ctx, x, y, width, height, r, thick, bc);
      }
   }
}
