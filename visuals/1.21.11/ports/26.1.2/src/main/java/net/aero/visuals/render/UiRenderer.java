package net.aero.visuals.render;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Быстрые 2D-примитивы для HUD и экранов; координаты уже в GUI-space. */
public final class UiRenderer {
    private UiRenderer() {}

    public static void rect(GuiGraphicsExtractor context, double x, double y, double width, double height, int color) {
        context.fill(round(x), round(y), round(x + width), round(y + height), color);
    }

    private static final int MAX_CACHE_R = 64;
    private static final int[][] INSETS_CACHE = new int[MAX_CACHE_R + 1][];
    private static final int[] FIRST_ZERO_CACHE = new int[MAX_CACHE_R + 1];

    static {
        for (int sr = 1; sr <= MAX_CACHE_R; sr++) {
            int[] insets = new int[sr];
            double rSq = (double) sr * sr;
            int firstZero = sr;
            for (int i = 0; i < sr; i++) {
                double dy = sr - (i + 0.5);
                double dx = Math.sqrt(Math.max(0.0, rSq - dy * dy));
                int inset = (int) Math.round(sr - dx);
                if (i > 0 && inset > insets[i - 1]) {
                    inset = insets[i - 1];
                }
                inset = Math.max(0, Math.min(sr - 1, inset));
                insets[i] = inset;
                if (inset == 0 && firstZero == sr) {
                    firstZero = i;
                }
            }
            insets[sr - 1] = 0;
            if (firstZero == sr) firstZero = sr - 1;
            INSETS_CACHE[sr] = insets;
            FIRST_ZERO_CACHE[sr] = firstZero;
        }
    }

    public static void roundedRect(GuiGraphicsExtractor context, double x, double y, double width, double height,
                                   double radius, int color) {
        if (width <= 0 || height <= 0) return;
        int alpha = (color >>> 24);
        if (alpha <= 0) return;

        double maxR = Math.min(width, height) * 0.5;
        double r = Math.max(0.0, Math.min(radius, maxR));
        if (r <= 0.75) {
            rect(context, x, y, width, height, color);
            return;
        }

        int sx = (int) Math.round(x);
        int sy = (int) Math.round(y);
        int sw = (int) Math.round(x + width) - sx;
        int sh = (int) Math.round(y + height) - sy;
        int sr = (int) Math.round(r);
        sr = Math.min(sr, Math.min(sw / 2, sh / 2));

        if (sr <= 1) {
            context.fill(sx, sy, sx + sw, sy + sh, color);
            return;
        }

        // Nine-slice coverage mask: real alpha at the curved edge, not staircase fills.
        int edge=Math.min(sr,Math.min(sw/2,sh/2)),cx=sx+edge,cy=sy+edge;
        context.fill(cx,sy,sx+sw-edge,sy+sh,color);
        if(sh>edge*2){context.fill(sx,cy,cx,sy+sh-edge,color);context.fill(sx+sw-edge,cy,sx+sw,sy+sh-edge,color);}
        Identifier mask=Identifier.fromNamespaceAndPath("aerovisuals","textures/gui/rounded_mask.png");
        textureRegion(context,mask,sx,sy,edge,edge,0,0,32,32,64,64,color);
        textureRegion(context,mask,sx+sw-edge,sy,edge,edge,32,0,32,32,64,64,color);
        textureRegion(context,mask,sx,sy+sh-edge,edge,edge,0,32,32,32,64,64,color);
        textureRegion(context,mask,sx+sw-edge,sy+sh-edge,edge,edge,32,32,32,32,64,64,color);
    }

    public static void shadow(GuiGraphicsExtractor context, double x, double y, double width, double height,
                              double radius, int color) {
        int baseAlpha = (color >>> 24);
        if (baseAlpha <= 0) return;
        int lAlpha = Math.max(2, (int) (baseAlpha * 0.35f));
        roundedRect(context, x - 2.0, y - 1.0, width + 4.0, height + 4.0, radius + 2.0, ColorUtil.withAlpha(color, lAlpha));
    }

    public static void fastRoundedRect(GuiGraphicsExtractor context, double x, double y, double width, double height,
                                       double radius, int color) {
        roundedRect(context, x, y, width, height, radius, color);
    }

    public static void border(GuiGraphicsExtractor context, double x, double y, double width, double height,
                              double thickness, int color) {
        rect(context, x, y, width, thickness, color);
        rect(context, x, y + height - thickness, width, thickness, color);
        rect(context, x, y, thickness, height, color);
        rect(context, x + width - thickness, y, thickness, height, color);
    }

    /** Draws a continuous, dot-free rounded border without corner gaps or floating dots with high FPS. */
    public static void roundedBorder(GuiGraphicsExtractor context, double x, double y, double width, double height,
                                     double radius, double thickness, int color) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;
        int alpha = (color >>> 24);
        if (alpha <= 0) return;

        double maxR = Math.min(width, height) * 0.5;
        double r = Math.max(0.0, Math.min(radius, maxR));
        double t = Math.max(0.5, Math.min(thickness, r));
        if (r <= 0.75) {
            border(context, x, y, width, height, t, color);
            return;
        }

        int sx = (int) Math.round(x);
        int sy = (int) Math.round(y);
        int sw = (int) Math.round(x + width) - sx;
        int sh = (int) Math.round(y + height) - sy;
        int sr = (int) Math.round(r);
        int st = Math.max(1, (int) Math.round(t));
        sr = Math.min(sr, Math.min(sw / 2, sh / 2));

        if (sr <= 1) {
            border(context, sx, sy, sw, sh, st, color);
            return;
        }

        // Horizontal straight bars
        int xBarStart = sx + sr;
        int xBarEnd = sx + sw - sr;
        if (xBarEnd > xBarStart) {
            context.fill(xBarStart, sy, xBarEnd, sy + st, color);
            context.fill(xBarStart, sy + sh - st, xBarEnd, sy + sh, color);
        }

        // Vertical straight bars
        int yBarStart = sy + sr;
        int yBarEnd = sy + sh - sr;
        if (yBarEnd > yBarStart) {
            context.fill(sx, yBarStart, sx + st, yBarEnd, color);
            context.fill(sx + sw - st, yBarStart, sx + sw, yBarEnd, color);
        }

        int[] insets = (sr <= MAX_CACHE_R) ? INSETS_CACHE[sr] : computeCornerInsets(sr);
        int innerR = Math.max(0, sr - st);
        int[] inInsets = (innerR > 0) ? ((innerR <= MAX_CACHE_R) ? INSETS_CACHE[innerR] : computeCornerInsets(innerR)) : null;

        // Run-length merge corner contour spans without pixel gaps or floating dots
        int i = 0;
        while (i < sr) {
            int xOuter = insets[i];
            int xInner = (i < st) ? sr : (innerR > 0 && (i - st < innerR) ? st + inInsets[i - st] : st);
            xInner = Math.max(xOuter + st, xInner);

            int run = 1;
            while (i + run < sr) {
                int nextOuter = insets[i + run];
                int nextInner = (i + run < st) ? sr : (innerR > 0 && (i + run - st < innerR) ? st + inInsets[i + run - st] : st);
                nextInner = Math.max(nextOuter + st, nextInner);
                if (nextOuter != xOuter || nextInner != xInner) break;
                run++;
            }

            int xL1 = sx + xOuter;
            int xL2 = Math.min(sx + sw / 2, sx + xInner);

            int xR2 = sx + sw - xOuter;
            int xR1 = Math.max(sx + sw / 2, sx + sw - xInner);

            if (xL2 > xL1) {
                context.fill(xL1, sy + i, xL2, sy + i + run, color);
                context.fill(xL1, sy + sh - i - run, xL2, sy + sh - i, color);
            }
            if (xR2 > xR1) {
                context.fill(xR1, sy + i, xR2, sy + i + run, color);
                context.fill(xR1, sy + sh - i - run, xR2, sy + sh - i, color);
            }
            i += run;
        }
    }

    private static int[] computeCornerInsets(int sr) {
        int[] insets = new int[sr];
        double rSq = (double) sr * sr;
        for (int i = 0; i < sr; i++) {
            double dy = sr - (i + 0.5);
            double dx = Math.sqrt(Math.max(0.0, rSq - dy * dy));
            int inset = (int) Math.round(sr - dx);
            if (i > 0 && inset > insets[i - 1]) {
                inset = insets[i - 1];
            }
            insets[i] = Math.max(0, Math.min(sr - 1, inset));
        }
        insets[sr - 1] = 0;
        return insets;
    }

    public static void verticalGradient(GuiGraphicsExtractor context, double x, double y, double width, double height,
                                        int topColor, int bottomColor) {
        context.fillGradient(round(x), round(y), round(x + width), round(y + height), topColor, bottomColor);
    }

    public static void horizontalGradient(GuiGraphicsExtractor context, double x, double y, double width, double height,
                                          int leftColor, int rightColor) {
        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        context.pose().rotate((float) (-Math.PI / 2.0));
        context.fillGradient(round(-height), 0, 0, round(width), leftColor, rightColor);
        context.pose().popMatrix();
    }

    public static void verticalGradientRoundedRect(GuiGraphicsExtractor context, double x, double y,
                                                   double width, double height, double radius,
                                                   int topColor, int bottomColor) {
        if (width <= 0 || height <= 0) return;
        double maxR = Math.min(width, height) * 0.5;
        double r = Math.max(0.0, Math.min(radius, maxR));
        if (r <= 0.5) {
            context.fillGradient(round(x), round(y), round(x + width), round(y + height),
                    topColor, bottomColor);
            return;
        }

        int sx = (int) Math.round(x);
        int sy = (int) Math.round(y);
        int sw = (int) Math.round(x + width) - sx;
        int sh = (int) Math.round(y + height) - sy;
        int sr = (int) Math.round(r);
        sr = Math.min(sr, Math.min(sw / 2, sh / 2));

        if (sr <= 0) {
            context.fillGradient(sx, sy, sx + sw, sy + sh, topColor, bottomColor);
            return;
        }

        int[] insets = (sr <= MAX_CACHE_R) ? INSETS_CACHE[sr] : computeCornerInsets(sr);
        int arcEnd = (sr <= MAX_CACHE_R) ? FIRST_ZERO_CACHE[sr] : sr;

        int bodyY1 = sy + arcEnd;
        int bodyY2 = sy + sh - arcEnd;
        int bodyTopColor = ColorUtil.mix(topColor, bottomColor, (float) arcEnd / Math.max(1, sh));
        int bodyBotColor = ColorUtil.mix(topColor, bottomColor, (float) (sh - arcEnd) / Math.max(1, sh));

        if (bodyY2 > bodyY1) {
            context.fillGradient(sx, bodyY1, sx + sw, bodyY2, bodyTopColor, bodyBotColor);
        }

        for (int i = 0; i < arcEnd; i++) {
            int inset = insets[i];
            int iLeft = sx + inset;
            int iRight = sx + sw - inset;

            if (iRight > iLeft) {
                int topY = sy + i;
                int cTop = ColorUtil.mix(topColor, bottomColor, (float) i / Math.max(1, sh));
                context.fill(iLeft, topY, iRight, topY + 1, cTop);

                int botY = sy + sh - 1 - i;
                int cBot = ColorUtil.mix(topColor, bottomColor, (float) (sh - 1 - i) / Math.max(1, sh));
                context.fill(iLeft, botY, iRight, botY + 1, cBot);
            }
        }
    }

    /** A shader-free horizontal gradient with rounded end caps. */
    public static void gradientRoundedRect(GuiGraphicsExtractor context, double x, double y, double width, double height,
                                           double radius, int leftColor, int rightColor) {
        double r = Math.max(0, Math.min(radius, Math.min(width, height) / 2.0));
        roundedRect(context, x, y, width, height, r, leftColor);
        if (width <= r * 2.0) return;
        horizontalGradient(context, x + r, y, width - r * 2.0, height, leftColor, rightColor);
        roundedRect(context, x + width - r * 2.0, y, r * 2.0, height, r, rightColor);
    }

    public static void text(GuiGraphicsExtractor context, String value, double x, double y, int color, boolean shadow) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) {
            context.text(Minecraft.getInstance().font, value, round(x), round(y), color, shadow);
            return;
        }
        if (shadow) runtime.renderEngine().fonts().draw(context, value, (float) x + 1, (float) y + 1,
                9.0F, ColorUtil.withAlpha(0xFF000000, Math.max(70, (color >>> 24) / 2)));
        runtime.renderEngine().fonts().draw(context, value, (float) x, (float) y, 9.0F, color);
    }

    public static int textWidth(String value) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? Minecraft.getInstance().font.width(value)
                : Math.round(runtime.renderEngine().fonts().width(value, 9.0F));
    }

    public static void textScaled(GuiGraphicsExtractor context, String value, double x, double y, int color,
                                  boolean shadow, double scale) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) {
            context.pose().pushMatrix();
            context.pose().translate((float) x, (float) y);
            context.pose().scale((float) scale, (float) scale);
            context.text(Minecraft.getInstance().font, value, 0, 0, color, shadow);
            context.pose().popMatrix();
            return;
        }
        float size = (float) (9.0 * scale);
        if (shadow) runtime.renderEngine().fonts().draw(context, value, (float) x + 1, (float) y + 1,
                size, ColorUtil.withAlpha(0xFF000000, Math.max(70, (color >>> 24) / 2)));
        runtime.renderEngine().fonts().draw(context, value, (float) x, (float) y, size, color);
    }

    public static void font(GuiGraphicsExtractor context, FontFace face, String value,
                            double x, double y, double size, int color) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) {
            textScaled(context, value, x, y, color, false, size / 9.0);
            return;
        }
        runtime.renderEngine().fonts().draw(context, face, value, (float) x, (float) y, (float) size, color);
    }

    public static String fitFont(FontFace face, String value, double maxWidth, double size, boolean stripTrailing) {
        if (value == null || value.isEmpty()) return "";
        ClientRuntime runtime = ClientRuntime.getNullable();
        float fontSize = (float) size;
        if (runtime != null && value.indexOf('§') < 0 && fontSize > 0 && Float.isFinite(fontSize)) {
            String fitted = runtime.renderEngine().fonts().fitBundled(face, value, maxWidth, fontSize, stripTrailing);
            if (fitted != null) return fitted;
        }
        return fitText(value, maxWidth, "…", stripTrailing, text -> fontWidth(face, text, size));
    }

    public static String fitScaledText(String value, double maxWidth, double scale) {
        return fitText(value, maxWidth, "...", true, text -> textWidth(text) * scale);
    }

    private static String fitText(String value, double maxWidth, String suffix, boolean stripTrailing,
                                  java.util.function.ToDoubleFunction<String> measure) {
        if (value == null || value.isEmpty()) return "";
        if (measure.applyAsDouble(value) <= maxWidth) return value;
        boolean suffixFits = measure.applyAsDouble(suffix) <= maxWidth;
        if (value.indexOf('§') >= 0) {
            // Vanilla formatting can change advances and consume the next character.
            for (int end = value.length(); end > 0;) {
                end = value.offsetByCodePoints(end, -1);
                if (end > 0 && value.charAt(end - 1) == '§') continue;
                String candidate = fittedPrefix(value, end, suffix, stripTrailing);
                if (measure.applyAsDouble(candidate) <= maxWidth) return candidate;
            }
            return suffixFits ? suffix : "";
        }
        if (!suffixFits) return "";

        int low = 1;
        int high = value.length() - 1;
        String best = suffix;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            int end = middle;
            if (Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) {
                end--;
            }
            String candidate = fittedPrefix(value, end, suffix, stripTrailing);
            if (measure.applyAsDouble(candidate) <= maxWidth) {
                best = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private static String fittedPrefix(String value, int end, String suffix, boolean stripTrailing) {
        String prefix = value.substring(0, end);
        return (stripTrailing ? prefix.stripTrailing() : prefix) + suffix;
    }

    public static double fontWidth(FontFace face, String value, double size) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? Minecraft.getInstance().font.width(value) * size / 9.0
                : runtime.renderEngine().fonts().width(face, value, (float) size);
    }

    /** Minecraft's batched font path, used by dense screens for maximum clarity and speed. */
    public static void vanillaText(GuiGraphicsExtractor context, String value, double x, double y,
                                   double scale, int color, boolean shadow) {
        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        context.pose().scale((float) scale, (float) scale);
        context.text(Minecraft.getInstance().font, value, 0, 0, color, shadow);
        context.pose().popMatrix();
    }

    public static double vanillaTextWidth(String value, double scale) {
        return Minecraft.getInstance().font.width(value) * scale;
    }

    public static void spacedFont(GuiGraphicsExtractor context, FontFace face, String value,
                                  double x, double y, double size, double spacing, int color) {
        double cursor = x;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            font(context, face, glyph, cursor, y, size, color);
            cursor += fontWidth(face, glyph, size) + spacing;
            offset += Character.charCount(codePoint);
        }
    }

    public static double spacedFontWidth(FontFace face, String value, double size, double spacing) {
        double width = 0;
        int count = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            width += fontWidth(face, new String(Character.toChars(codePoint)), size);
            count++;
            offset += Character.charCount(codePoint);
        }
        return width + Math.max(0, count - 1) * spacing;
    }

    public static void spacedText(GuiGraphicsExtractor context, String value, double x, double y, int color,
                                  double scale, int spacing) {
        double cursor = x;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            textScaled(context, glyph, cursor, y, color, false, scale);
            cursor += (textWidth(glyph) + spacing) * scale;
            offset += Character.charCount(codePoint);
        }
    }

    public static double spacedTextWidth(String value, double scale, int spacing) {
        double width = 0;
        int glyphs = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            width += textWidth(new String(Character.toChars(codePoint))) * scale;
            glyphs++;
            offset += Character.charCount(codePoint);
        }
        return width + Math.max(0, glyphs - 1) * spacing * scale;
    }

    /** Draws a complete PNG at GUI coordinates without making callers repeat pipeline boilerplate. */
    public static void texture(GuiGraphicsExtractor context, Identifier texture, double x, double y,
                               double width, double height, int textureWidth, int textureHeight) {
        context.blit(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y), 0, 0,
                round(width), round(height), textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** Draws a complete PNG with an ARGB multiplier, useful for restrained background artwork. */
    public static void texture(GuiGraphicsExtractor context, Identifier texture, double x, double y,
                               double width, double height, int textureWidth, int textureHeight, int color) {
        context.blit(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y), 0, 0,
                round(width), round(height), textureWidth, textureHeight, textureWidth, textureHeight, color);
    }

    /** Draws a full texture with a tiny source inset to prevent edge texel repetition when scaled. */
    public static void textureInset(GuiGraphicsExtractor context, Identifier texture, double x, double y,
                                    double width, double height, int textureWidth, int textureHeight,
                                    int inset) {
        int safeInset = Math.max(0, Math.min(inset, Math.min(textureWidth, textureHeight) / 4));
        context.blit(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y),
                safeInset, safeInset, round(width), round(height),
                Math.max(1, textureWidth - safeInset * 2), Math.max(1, textureHeight - safeInset * 2),
                textureWidth, textureHeight);
    }

    public static void textureRegion(GuiGraphicsExtractor context, Identifier texture, double x, double y,
                                     double width, double height, float sourceX, float sourceY,
                                     int sourceWidth, int sourceHeight,
                                     int textureWidth, int textureHeight, int color) {
        context.blit(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y), sourceX, sourceY,
                round(width), round(height), sourceWidth, sourceHeight, textureWidth, textureHeight, color);
    }

    /** Draws a Minecraft GUI sprite, including its nine-slice metadata when present. */
    public static void guiSprite(GuiGraphicsExtractor context, Identifier sprite, double x, double y,
                                 double width, double height) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, round(x), round(y), round(width), round(height));
    }

    public static boolean inside(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static void line(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color) {
        rect(context, Math.min(x1, x2), Math.min(y1, y2), Math.max(1, Math.abs(x2 - x1)), Math.max(1, Math.abs(y2 - y1)), color);
    }

    public static void fill(GuiGraphicsExtractor context, double x, double y, double width, double height, int color) {
        rect(context, x, y, width, height, color);
    }

    public static void text(GuiGraphicsExtractor context, Object ignoredRenderer, String text, double x, double y, int color) {
        text(context, text, x, y, color, true);
    }

    public static void circle(GuiGraphicsExtractor context, double cx, double cy, double radius, int color) {
        roundedRect(context, cx - radius, cy - radius, radius * 2.0, radius * 2.0, radius, color);
    }

    public static void triangleRight(GuiGraphicsExtractor context, double x, double y, double width, double height, int color) {
        int w = (int) Math.round(width);
        double halfH = height / 2.0;
        int sy = (int) Math.round(y);
        int sx = (int) Math.round(x);
        for (int i = 0; i < w; i++) {
            double progress = (double) i / Math.max(1, w);
            int top = (int) Math.round(sy + halfH * (1.0 - progress));
            int bot = (int) Math.round(sy + halfH * (1.0 + progress));
            if (bot > top) {
                context.fill(sx + i, top, sx + i + 1, bot, color);
            }
        }
    }

    public static void triangleLeft(GuiGraphicsExtractor context, double x, double y, double width, double height, int color) {
        int w = (int) Math.round(width);
        double halfH = height / 2.0;
        int sy = (int) Math.round(y);
        int sx = (int) Math.round(x);
        for (int i = 0; i < w; i++) {
            double progress = (double) (w - 1 - i) / Math.max(1, w);
            int top = (int) Math.round(sy + halfH * (1.0 - progress));
            int bot = (int) Math.round(sy + halfH * (1.0 + progress));
            if (bot > top) {
                context.fill(sx + i, top, sx + i + 1, bot, color);
            }
        }
    }

    /** Размытие полностью отключено по требованию. */
    public static void tryBlur(GuiGraphicsExtractor context) {
        // no-op
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static int colorAt(int top, int bottom, int row, int height) {
        return ColorUtil.mix(top, bottom, row / (float) Math.max(1, height - 1));
    }
}
