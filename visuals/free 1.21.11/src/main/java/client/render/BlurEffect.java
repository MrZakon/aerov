package client.render;

import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;

public final class BlurEffect {
   private BlurEffect() {}

   public static void update2() {}

   public static void onFloatFloatFloat(float value, float value2, float value3) {}

   public static void onFloatFloatFloatFloatFloatFloatFloatFloatFloat(
      float width, float y, float blurRadius, float alpha, float scale, float radius, float height, float x, float unused
   ) {
      if (width <= 0.0F || height <= 0.0F || alpha <= 0.001F) return;
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      int a = Math.clamp(Math.round(alpha * 70.0F), 0, 255);
      if (a <= 0) return;
      int glassColor = (a << 24) | 0x080C16;
      UiRenderer.roundedRect(ctx, x, y, width, height, radius, glassColor);
   }
}
