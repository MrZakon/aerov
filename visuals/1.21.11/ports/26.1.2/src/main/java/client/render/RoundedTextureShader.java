package client.render;

import client.util.MathUtil;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public class RoundedTextureShader {
   public static void onFloatIdentifierFloatFloatFloatMatrix4fFloat(
      float size, Identifier identifier, float y, float radius, float x, Matrix4f matrix4f, float alpha
   ) {
      GuiGraphicsExtractor ctx = ItemIconCache.getDrawContext();
      if (ctx == null || identifier == null || size <= 0.0F || alpha <= 0.0F) return;
      int color = MathUtil.getIntByFloatInt2(alpha, -1);
      UiRenderer.textureRegion(ctx, identifier, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64, color);
      UiRenderer.textureRegion(ctx, identifier, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64, color);
   }

   public static void onFloatFloatMatrix4fFloatIdentifierFloatFloatFloatInt(
      float width, float x, Matrix4f matrix4f, float height, Identifier identifier, float alpha, float y, float radius, int color
   ) {
      GuiGraphicsExtractor ctx = ItemIconCache.getDrawContext();
      if (ctx == null || identifier == null || width <= 0.0F || height <= 0.0F || alpha <= 0.0F) return;
      int c = MathUtil.getIntByFloatInt2(alpha, color);
      UiRenderer.texture(ctx, identifier, x, y, width, height, 64, 64, c);
   }

   public static void onFloatIntIntFloatFloatFloatFloatFloatFloatFloatFloatFloatFloatFloatMatrix4fFloat(
      float value, int count, int count2, float value2, float value3, float value4, float value5, float value6,
      float value7, float value8, float value9, float value10, float value11, float value12, Matrix4f matrix4f, float value13
   ) {
   }

   public static void onIntIdentifierFloatArrayFloatFloat(int count, Identifier identifier, float[] valueArray, float value, float value2) {
   }

   public static void onFloatMatrix4fFloatFloatFloatFloatFloatIntFloatIdentifierFloatFloatFloat(
      float radius, Matrix4f matrix4f, float alpha, float v0, float u0, float width, float height,
      int color, float y, Identifier identifier, float u1, float x, float v1
   ) {
      GuiGraphicsExtractor ctx = ItemIconCache.getDrawContext();
      if (ctx == null || identifier == null || width <= 0.0F || height <= 0.0F || alpha <= 0.0F) return;
      int c = MathUtil.getIntByFloatInt2(alpha, color);
      int texW = 256;
      int texH = 256;
      float srcX = u0 * texW;
      float srcY = v0 * texH;
      int srcW = Math.max(1, Math.round((u1 - u0) * texW));
      int srcH = Math.max(1, Math.round((v1 - v0) * texH));
      UiRenderer.textureRegion(ctx, identifier, x, y, width, height, srcX, srcY, srcW, srcH, texW, texH, c);
   }

   public static void onFloatFloatFloatFloatFloatFloatIntFloatFloatMatrix4fIdentifierFloatFloatFloatFloatFloat(
      float x, float width, float u1, float height, float v1, float r1, int color, float y, float r2,
      Matrix4f matrix4f, Identifier identifier, float r3, float u0, float v0, float alpha, float r4
   ) {
      onFloatMatrix4fFloatFloatFloatFloatFloatIntFloatIdentifierFloatFloatFloat(
         r1, matrix4f, alpha, v0, u0, width, height, color, y, identifier, u1, x, v1
      );
   }
}
