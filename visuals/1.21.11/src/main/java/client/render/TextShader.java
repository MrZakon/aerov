package client.render;

import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextShader {
   private static int unscaledDepth;

   public static void update() {
      unscaledDepth = Math.max(0, unscaledDepth - 1);
   }
   public static void update2() {}
   public static void update3() {
      unscaledDepth++;
   }

   public static float getTextScale() {
      return unscaledDepth == 0 && client.gui.screen.ClickGuiScreen.isClientGuiOpen
         ? client.util.InterfaceSizing.getTextScale() : 1.0F;
   }

   public static int applyAlpha(int color, float alpha) {
      if (alpha <= 0.0F) return 0;
      int a = (color >>> 24);
      if (a == 0) a = 255;
      int finalA = Math.clamp(Math.round(a * Math.clamp(alpha, 0.0F, 1.0F)), 0, 255);
      return (color & 0x00FFFFFF) | (finalA << 24);
   }

   public static String join(String[] arr) {
      if (arr == null || arr.length == 0) return "";
      StringBuilder sb = new StringBuilder();
      for (String s : arr) {
         if (s != null) sb.append(s);
      }
      return sb.toString();
   }

   public static String joinSlice(String[] arr, int start, int end) {
      if (arr == null || arr.length == 0) return "";
      int s = Math.max(0, start);
      int e = Math.min(arr.length, end);
      if (s >= e) return "";
      StringBuilder sb = new StringBuilder();
      for (int i = s; i < e; i++) {
         if (arr[i] != null) sb.append(arr[i]);
      }
      return sb.toString();
   }

   private static final java.util.Map<String, List<String>> WRAP_CACHE = new java.util.concurrent.ConcurrentHashMap<>(256);

   public static void clearWrapCache() {
      WRAP_CACHE.clear();
   }

   public static List<String> wrap(String text, float wrapWidth, float size) {
      text=net.aero.visuals.localization.LocalText.get(text);
      if (text == null || text.isEmpty()) return Collections.emptyList();
      if (wrapWidth <= 0.0F) return Collections.singletonList(text);
      String key = Float.floatToIntBits(wrapWidth) + "_" + Float.floatToIntBits(size * getTextScale()) + "_" + text;
      List<String> cached = WRAP_CACHE.get(key);
      if (cached != null) return cached;

      List<String> lines = new ArrayList<>();
      java.util.List<String> tokens=new java.util.ArrayList<>();for(String word:text.split(" ")){StringBuilder part=new StringBuilder();for(int code:word.codePoints().toArray()){String next=part.toString()+new String(Character.toChars(code));if(part.length()>0&&getFloatByFloatString(size,next)>wrapWidth){tokens.add(part.toString());part.setLength(0);}part.appendCodePoint(code);}if(part.length()>0)tokens.add(part.toString());}
      String[] words = tokens.toArray(new String[0]);
      StringBuilder cur = new StringBuilder();
      for (String word : words) {
         if (cur.length() == 0) {
            cur.append(word);
         } else {
            String candidate = cur + " " + word;
            if (getFloatByFloatString(size, candidate) <= wrapWidth) {
               cur.append(" ").append(word);
            } else {
               lines.add(cur.toString());
               cur = new StringBuilder(word);
            }
         }
      }
      if (cur.length() > 0) {
         lines.add(cur.toString());
      }
      List<String> unmodifiable = Collections.unmodifiableList(lines);
      if (WRAP_CACHE.size() < 2048) {
         WRAP_CACHE.put(key, unmodifiable);
      }
      return unmodifiable;
   }

   public static float getFloatByFloat3(float value) {
      return value * getTextScale() * 1.2F;
   }

   public static float getFloatByFloatString(float size, String text) {
      if (text == null || text.isEmpty()) return 0.0F;
      return (float) UiRenderer.fontWidth(FontFace.GOLOS, net.aero.visuals.localization.LocalText.get(text), size * getTextScale());
   }

   public static float getFloatByStringFloat(String text, float size) {
      return getFloatByFloatString(size, text);
   }

   public static float getFloatByFloatFloatString(float wrapWidth, float size, String text) {
      List<String> lines = wrap(text, wrapWidth, size);
      return lines.isEmpty() ? 0.0F : lines.size() * getFloatByFloat3(size);
   }

   public static float getFloatByFloatStringArray(float size, String[] textArray) {
      if (textArray == null || textArray.length == 0) return 0.0F;
      return getFloatByFloatString(size, join(textArray));
   }

   public static float getFloatByFloatFloatStringArray(float wrapWidth, float size, String[] textArray) {
      return getFloatByFloatFloatString(wrapWidth, size, join(textArray));
   }

   public static float getFloatByIntStringArrayFloatInt(int startIndex, String[] textArray, float size, int endIndex) {
      return getFloatByFloatString(size, joinSlice(textArray, startIndex, endIndex));
   }

   public static int getIntByFloatFloatStringArray(float size, float maxWidth, String[] textArray) {
      if (textArray == null || textArray.length == 0 || maxWidth <= 0.0F) return 0;
      for (int i = textArray.length; i > 0; i--) {
         if (getFloatByIntStringArrayFloatInt(0, textArray, size, i) <= maxWidth) {
            return i;
         }
      }
      return 0;
   }

   public static void onMatrix4fStringFloatFloatFloatIntFloat(Matrix4f matrix4f, String text, float x, float y, float size, int color, float alpha) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || text == null || text.isEmpty()) return;
      UiRenderer.font(ctx, FontFace.GOLOS, text, x, y, size * getTextScale(), applyAlpha(color, alpha));
   }

   public static void onMatrix4fStringFloatFloatFloatIntFloat2(Matrix4f matrix4f, String text, float x, float y, float size, int color, float alpha) {
      onMatrix4fStringFloatFloatFloatIntFloat(matrix4f, text, x, y, size, color, alpha);
   }

   public static void onMatrix4fStringFloatFloatFloatIntFloat3(Matrix4f matrix4f, String text, float x, float y, float size, int color, float alpha) {
      onMatrix4fStringFloatFloatFloatIntFloat(matrix4f, text, x, y, size, color, alpha);
   }

   public static void onFloatFloatIntFloatFloatStringMatrix4f(float y, float x, int color, float size, float alpha, String text, Matrix4f matrix4f) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || text == null || text.isEmpty()) return;
      UiRenderer.font(ctx, FontFace.GOLOS, text, x, y, size * getTextScale(), applyAlpha(color, alpha));
   }

   public static void onMatrix4fFloatFloatIntFloatStringFloatFloat(
      Matrix4f matrix4f, float y, float alpha, int color, float blur, String text, float size, float x
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || text == null || text.isEmpty()) return;
      UiRenderer.font(ctx, FontFace.GOLOS, text, x, y, size * getTextScale(), applyAlpha(color, alpha));
   }

   public static void onIntStringFloatMatrix4fFloatFloatFloatFloat(
      int color, String text, float blur, Matrix4f matrix4f, float y, float alpha, float size, float x
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || text == null || text.isEmpty()) return;
      UiRenderer.font(ctx, FontFace.GOLOS, text, x, y, size * getTextScale(), applyAlpha(color, alpha));
   }

   public static void onFloatStringFloatFloatIntFloatMatrix4f(float alpha, String text, float size, float x, int color, float y, Matrix4f matrix4f) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || text == null || text.isEmpty()) return;
      UiRenderer.font(ctx, FontFace.GOLOS, text, x, y, size * getTextScale(), applyAlpha(color, alpha));
   }

   public static void onFloatStringFloatFloatMatrix4fFloatFloatInt(
      float alpha, String text, float x, float size, Matrix4f matrix4f, float wrapWidth, float y, int color
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || text == null || text.isEmpty()) return;
      List<String> lines = wrap(text, wrapWidth, size);
      float curY = y;
      int c = applyAlpha(color, alpha);
      float lineH = getFloatByFloat3(size);
      for (String line : lines) {
         if (line != null && !line.isEmpty()) {
            UiRenderer.font(ctx, FontFace.GOLOS, line, x, curY, size * getTextScale(), c);
         }
         curY += lineH;
      }
   }

   public static void onIntFloatFloatMatrix4fFloatFloatString(int color, float alpha, float y, Matrix4f matrix4f, float x, float size, String text) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || text == null || text.isEmpty()) return;
      UiRenderer.font(ctx, FontFace.GOLOS, text, x, y, size * getTextScale(), applyAlpha(color, alpha));
   }

   public static void onFloatFloatIntFloatMatrix4fStringFloatFloat(
      float x, float y, int color, float wrapWidth, Matrix4f matrix4f, String text, float alpha, float size
   ) {
      onFloatStringFloatFloatMatrix4fFloatFloatInt(alpha, text, x, size, matrix4f, wrapWidth, y, color);
   }

   public static void onMatrix4fFloatFloatStringFloatIntFloatFloat(
      Matrix4f matrix4f, float alpha, float x, String text, float size, int color, float wrapWidth, float y
   ) {
      onFloatStringFloatFloatMatrix4fFloatFloatInt(alpha, text, x, size, matrix4f, wrapWidth, y, color);
   }

   public static void onFloatFloatFloatStringArrayFloatIntMatrix4fFloat(
      float y, float alpha, float wrapWidth, String[] textArray, float size, int color, Matrix4f matrix4f, float x
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || textArray == null) return;
      List<String> lines = wrap(join(textArray), wrapWidth, size);
      float curY = y;
      int c = applyAlpha(color, alpha);
      float lineH = getFloatByFloat3(size);
      for (String line : lines) {
         if (line != null && !line.isEmpty()) {
            UiRenderer.font(ctx, FontFace.GOLOS, line, x, curY, size * getTextScale(), c);
         }
         curY += lineH;
      }
   }

   public static void onIntFloatStringArrayMatrix4fFloatFloatFloatFloat(
      int color, float alpha, String[] textArray, Matrix4f matrix4f, float x, float wrapWidth, float y, float size
   ) {
      onFloatFloatFloatStringArrayFloatIntMatrix4fFloat(y, alpha, wrapWidth, textArray, size, color, matrix4f, x);
   }

   public static void onIntFloatStringArrayFloatMatrix4fIntIntFloatFloat(
      int startIndex, float alpha, String[] textArray, float x, Matrix4f matrix4f, int endIndex, int color, float size, float y
   ) {
      DrawContext ctx = ItemIconCache.getDrawContext();
      if (ctx == null || textArray == null) return;
      String text = joinSlice(textArray, startIndex, endIndex);
      if (!text.isEmpty()) {
         UiRenderer.font(ctx, FontFace.GOLOS, text, x, y, size * getTextScale(), applyAlpha(color, alpha));
      }
   }
}
