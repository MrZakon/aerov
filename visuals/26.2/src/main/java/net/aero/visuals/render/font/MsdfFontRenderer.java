package net.aero.visuals.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** DrawContext-compatible renderer for every MSDF face shipped with the original menu assets. */
public final class MsdfFontRenderer implements AutoCloseable {
    private static final float SUBPIXEL_PRECISION = 4.0F;
    private static final int WIDTHS_PER_FACE = 4096 / FontFace.values().length;
    private static final int MAX_CACHED_TEXT_LENGTH = 256;
    private final Map<FontFace, LoadedFace> faces = new EnumMap<>(FontFace.class);
    private final Set<FontFace> unavailable = EnumSet.noneOf(FontFace.class);

    private LoadedFace loadFace(FontFace face) {
        LoadedFace known = faces.get(face);
        if (known != null || unavailable.contains(face)) return known;
        Minecraft client = Minecraft.getInstance();
        try {
            Identifier metrics = source(face, "json");
            Identifier image = source(face, "png");
            Identifier raster = Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID, "runtime/font_" + face.file());
            MsdfFont font = MsdfFont.load(client.getResourceManager(), metrics, image);
            NativeImage pixels = rasterizeAtlas(client, font, image);
            LinearFontTexture texture = null;
            try {
                texture = new LinearFontTexture(() -> "AeroVisuals " + face.file() + " alpha atlas", pixels);
                pixels = null;
                texture.applyAtlasSampler();
                client.getTextureManager().register(raster, texture);
                texture = null;
            } finally {
                if (texture != null) texture.close();
                if (pixels != null) pixels.close();
            }
            var loaded = new LoadedFace(font, raster, new LinkedHashMap<>(128, 0.75f, true));
            faces.put(face, loaded);
            return loaded;
        } catch (IOException | RuntimeException failure) {
            unavailable.add(face);
            AeroVisuals.LOGGER.warn("Could not initialize bundled font {}; using the vanilla font", face.file(), failure);
            return null;
        }
    }

    public float width(String text, float size) {
        return width(FontFace.SF_REGULAR, text, size);
    }

    public float width(FontFace face, String text, float size) {
        if (text == null || text.isEmpty()) return 0.0F;
        LoadedFace loaded = loadFace(face);
        if (loaded == null) return Minecraft.getInstance().font.width(text) * (size / 9.0F);

        return baseWidth(loaded, text) * size;
    }

    /**
     * Fits plain text using the same advances as width(). Returns null when the
     * bundled face is unavailable and the caller should measure the vanilla font.
     */
    public String fitBundled(FontFace face, String text, double maxWidth, float size, boolean stripTrailing) {
        LoadedFace loaded = loadFace(face);
        if (loaded == null) return null;
        if (baseWidth(loaded, text) * size <= maxWidth) return text;

        final String suffix = "…";
        float suffixAdvance = advance(loaded.font, '…');
        if (!(suffixAdvance * size <= maxWidth)) return "";

        float prefixAdvance = 0;
        int bestEnd = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (offset == text.length()) break;
            prefixAdvance += advance(loaded.font, codePoint);
            if (stripTrailing && Character.isWhitespace(codePoint)) continue;
            if (!((prefixAdvance + suffixAdvance) * size <= maxWidth)) break;
            bestEnd = offset;
        }
        return bestEnd == 0 ? suffix : text.substring(0, bestEnd) + suffix;
    }

    private static float advance(MsdfFont font, int codePoint) {
        MsdfFont.Glyph glyph = font.glyph(codePoint);
        return glyph == null ? fallbackWidth(codePoint, 1.0F) : glyph.advance();
    }

    private static float baseWidth(LoadedFace loaded, String text) {
        boolean cacheable = text.length() <= MAX_CACHED_TEXT_LENGTH;
        Float cachedBase = cacheable ? loaded.widths.get(text) : null;
        if (cachedBase != null) {
            return cachedBase;
        }

        float base = 0.0F;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            base += advance(loaded.font, codePoint);
            offset += Character.charCount(codePoint);
        }
        if (cacheable) {
            if (loaded.widths.size() >= WIDTHS_PER_FACE) loaded.widths.pollFirstEntry();
            loaded.widths.put(text, base);
        }
        return base;
    }

    public void draw(GuiGraphicsExtractor context, String text, float x, float y, float size, int color) {
        draw(context, FontFace.SF_REGULAR, text, x, y, size, color);
    }

    public void draw(GuiGraphicsExtractor context, FontFace face, String text,
                     float x, float y, float size, int color) {
        if (text == null || text.isEmpty()) return;
        LoadedFace loaded = loadFace(face);
        if (loaded == null) {
            drawVanilla(context, text, x, y, size, color);
            return;
        }

        // DrawContext textures use integer destination rectangles. Rendering at 4x coordinates
        // under a reciprocal matrix scale preserves quarter-pixel glyph positions and fixes the
        // uneven Cyrillic spacing produced by rounding every letter independently.
        context.pose().pushMatrix();
        context.pose().scale(1.0F / SUBPIXEL_PRECISION, 1.0F / SUBPIXEL_PRECISION);
        try (var batch = HudGlyphBatch.begin(context, text)) {
            MsdfFont font = loaded.font;
            float preciseSize = size * SUBPIXEL_PRECISION;
            float cursor = x * SUBPIXEL_PRECISION;
            float preciseY = y * SUBPIXEL_PRECISION;
            float baseline = preciseY + font.ascender() * preciseSize;
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                MsdfFont.Glyph glyph = font.glyph(codePoint);
                if (glyph != null && glyph.plane() != null && glyph.atlas() != null) {
                    MsdfFont.Bounds plane = glyph.plane();
                    MsdfFont.Bounds atlas = glyph.atlas();
                    double drawX = cursor + plane.left() * preciseSize;
                    double drawY = baseline - plane.top() * preciseSize;
                    double drawWidth = (plane.right() - plane.left()) * preciseSize;
                    double drawHeight = (plane.top() - plane.bottom()) * preciseSize;
                    int sourceWidth = Math.max(1, Math.round(atlas.right() - atlas.left()));
                    int sourceHeight = Math.max(1, Math.round(atlas.top() - atlas.bottom()));
                    float sourceY = font.atlasHeight() - atlas.top();
                    UiRenderer.textureRegion(context, loaded.raster, drawX, drawY, drawWidth, drawHeight,
                            atlas.left(), sourceY, sourceWidth, sourceHeight,
                            Math.round(font.atlasWidth()), Math.round(font.atlasHeight()), color);
                } else if (glyph == null && !Character.isWhitespace(codePoint)) {
                    if (batch != null) batch.flush();
                    drawVanilla(context, Character.toString(codePoint), cursor, preciseY, preciseSize, color);
                }
                cursor += glyph == null ? fallbackWidth(codePoint, preciseSize)
                        : glyph.advance() * preciseSize;
                offset += Character.charCount(codePoint);
            }
        } finally {
            context.pose().popMatrix();
        }
    }

    private static float fallbackWidth(int codePoint, float size) {
        String value = new String(Character.toChars(codePoint));
        return Minecraft.getInstance().font.width(value) * (size / 9.0F);
    }

    private static void drawVanilla(GuiGraphicsExtractor context, String text,
                                    float x, float y, float size, int color) {
        float scale = size / 9.0F;
        var matrices = context.pose();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            context.text(Minecraft.getInstance().font, text, 0, 0, color, false);
        } finally {
            matrices.popMatrix();
        }
    }

    private static Identifier source(FontFace face, String extension) {
        return Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID, "fonts/" + face.file() + "." + extension);
    }

    private static NativeImage rasterizeAtlas(Minecraft client, MsdfFont font,
                                               Identifier source) throws IOException {
        var resource = client.getResourceManager().getResource(source)
                .orElseThrow(() -> new IOException("Missing font atlas " + source));
        NativeImage image = null;
        boolean complete = false;
        try {
            try (InputStream stream = resource.open()) {
                image = NativeImage.read(stream);
            }
            float range = Math.max(1.0F, font.distanceRange());
            // Preserve the soft edge while evaluating the curve only once per channel value.
            int[] pixelsByMedian = new int[256];
            for (int channel = 0; channel < pixelsByMedian.length; channel++) {
                float coverage = smoothstep(0.0F, 1.0F,
                        Math.clamp((channel / 255.0F - 0.5F) * (range * 0.72F) + 0.5F, 0.0F, 1.0F));
                pixelsByMedian[channel] = Math.round(coverage * 255.0F) << 24 | 0x00FFFFFF;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (font.atlasWidth() != width || font.atlasHeight() != height) {
                throw new IOException("Font atlas dimensions do not match metrics: " + source);
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getPixel(x, y);
                    int red = (argb >>> 16) & 255;
                    int green = (argb >>> 8) & 255;
                    int blue = argb & 255;
                    int median = Math.max(Math.min(red, green), Math.min(Math.max(red, green), blue));
                    image.setPixel(x, y, pixelsByMedian[median]);
                }
            }
            complete = true;
            return image;
        } finally {
            if (!complete && image != null) image.close();
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static final class LinearFontTexture extends DynamicTexture {
        private LinearFontTexture(java.util.function.Supplier<String> label, NativeImage image) {
            super(label, image);
            // The immutable atlas was uploaded by the superclass constructor.
            image.close();
        }

        private void applyAtlasSampler() {
            // Linear sampling keeps small glyph edges smooth.
            this.sampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
    }

    @Override
    public void close() {
        Minecraft client = Minecraft.getInstance();
        for (LoadedFace face : faces.values()) client.getTextureManager().release(face.raster);
        faces.clear();
        unavailable.clear();
    }

    private record LoadedFace(MsdfFont font, Identifier raster, LinkedHashMap<String, Float> widths) {}
}
