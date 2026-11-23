package client.render;

import client.module.CategoryType;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public class SvgShader {
   public static void update() {}
   public static void update2() {}
   public static void update3() {}
   public static void update4() {}

   private static int applyAlpha(int color, float alpha) {
      if (alpha <= 0.0F) return 0;
      int a = (color >>> 24);
      if (a == 0) a = 255;
      int finalA = Math.clamp(Math.round(a * Math.clamp(alpha, 0.0F, 1.0F)), 0, 255);
      return (color & 0x00FFFFFF) | (finalA << 24);
   }

   public static void onIntFloatFloatFloatFloatFloatMatrix4f(int count, float value, float value2, float value3, float value4, float value5, Matrix4f matrix4f) {
      float f = Math.min(value3, value2) * 0.2F;
      ShapeShader.onFloatFloatIntMatrix4fFloatFloatFloatFloat(f, value, count, matrix4f, value2, value3, value4, value5);
   }

   public static void onFloatFloatMatrix4fFloatFloatInt(float value, float value2, Matrix4f matrix4f, float value3, float value4, int count) {
      float f = value4 * 0.2F;
      ShapeShader.onFloatFloatIntMatrix4fFloatFloatFloatFloat(f, value3, count, matrix4f, value4, value4, value, value2);
   }

   public static void onFloatIntMatrix4fFloatCategoryTypeFloatFloatFloat(
      float value, int count2, Matrix4f matrix4f, float value2, CategoryType categoryType, float value3, float value4, float value5
   ) {
      GuiGraphicsExtractor ctx = ItemIconCache.getDrawContext();
      if (ctx == null) return;
      float alpha = value;
      int color = count2;
      float y = value2;
      float height = value3;
      float x = value4;
      float width = value5;

      int c = applyAlpha(color, alpha);
      Identifier texId = CategoryIconTexture.get(categoryType);
      if (texId != null) {
         float size = Math.min(width, height);
         float offX = x + (width - size) * 0.5F;
         float offY = y + (height - size) * 0.5F;
         UiRenderer.texture(ctx, texId, offX, offY, size, size, 64, 64, c);
         return;
      }
      String symbol = getCategorySymbol(categoryType);
      if (symbol != null && !symbol.isEmpty()) {
         float fontSize = Math.min(width, height) * 0.85F;
         float strW = (float) UiRenderer.fontWidth(FontFace.GOLOS_SEMI, symbol, fontSize);
         float offX = x + (width - strW) * 0.5F;
         float offY = y + (height - fontSize) * 0.5F;
         UiRenderer.font(ctx, FontFace.GOLOS_SEMI, symbol, offX, offY, fontSize, c);
      } else {
         float r = Math.min(width, height) * 0.25F;
         UiRenderer.roundedRect(ctx, x, y, width, height, r, c);
      }
   }

   public static void onFloatCategoryTypeIntMatrix4fFloatFloatFloat(float value, CategoryType categoryType, int count, Matrix4f matrix4f, float value2, float value3, float value4) {
      onFloatIntMatrix4fFloatCategoryTypeFloatFloatFloat(value2, count, matrix4f, value, categoryType, value3, value4, value3);
   }

   private static String getCategorySymbol(CategoryType type) {
      if (type == null) return "•";
      return switch (type) {
         case LOGO -> "⚡";
         case ALL -> "☰";
         case CLIENT -> "⚙";
         case COMBAT -> "⚔";
         case MOVEMENT -> "»";
         case PANEL -> "⊞";
         case PARTICLES -> "✨";
         case PLAYER -> "👤";
         case RENDER -> "👁";
         case SEARCH -> "🔍";
         case DROPDOWN_ARROWS, SETTING_DROPDOWN -> "⌄";
         case CHEVRON_RIGHT, ARROW_RIGHT -> "›";
         case ARROW_LEFT -> "‹";
         case DROPDOWN_SUCCESS, SUCCESS_CHECKBOX, SETTING_CHECKBOX -> "✓";
         case STAR, STAR_FILLED -> "★";
         case EXPAND_ALL -> "⤢";
         case COLLAPSE_ALL -> "⤡";
         case SUN -> "☼";
         case MOON -> "☾";
         case LANGUAGE, GLOBE -> "🌐";
         case KEYBOARD -> "⌨";
         case CLOSE -> "✕";
         case POTION -> "🧪";
         case HEART -> "♥";
         case COOLDOWN, TIMER_RING -> "⏱";
         case EDIT -> "✎";
         case SETTINGS -> "⚙";
         case VISUAL -> "✨";
         case CLOUDS -> "☁";
         case SAVE -> "💾";
         case SHARE -> "⇪";
         case TRASH -> "🗑";
         case INFO -> "ℹ";
         case FRIENDS -> "👥";
         case WAYPOINT, WAYPOINT_ARROW -> "📍";
         case COPY -> "⧉";
         case TOOLTIP_ARROW, TOOLTIP_ARROW_UP -> "▲";
         case TOOLTIP_ARROW_LEFT -> "◀";
         case TOOLTIP_ARROW_RIGHT -> "▶";
         case ALIGN_LEFT -> "⇤";
         case ALIGN_CENTER, SETTING_ALIGNMENT -> "≡";
         case ALIGN_RIGHT -> "⇥";
         case ARMOR_HUD -> "🛡";
         case TARGET_HUD -> "🎯";
         case SWAP_HUD -> "⇄";
         case BOLT -> "⚡";
         case RACK -> "☷";
         case CUBE -> "▣";
         case WATERMARK -> "✦";
         case PLUS -> "+";
         case KEY -> "🗝";
         case LOCK_OPEN -> "🔓";
         case LOCK_CLOSED -> "🔒";
         case ORIENT_VERTICAL -> "↕";
         case ORIENT_HORIZONTAL -> "↔";
         case COORDS -> "🧭";
         case SETTING_COLOR, BRUSH -> "🎨";
         case SETTING_SLIDER -> "―";
         case RESET -> "↺";
         case EYEDROPPER -> "💉";
         case USE_TRACKER -> "⌛";
         default -> "•";
      };
   }
}
