package net.aero.visuals.render.font;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;

/** Groups consecutive quads that share the same pipeline, texture, and clip into one GUI element. */
public final class HudGlyphBatch implements AutoCloseable {
    private static HudGlyphBatch current;
    private final DrawContext owner;
    private final HudGlyphBatch previous;
    private GuiRenderState state;
    private List<SimpleGuiElementRenderState> glyphs;
    private int left, top, right, bottom;

    private HudGlyphBatch(DrawContext owner) {
        this.owner = owner;
        previous = current;
        if (previous != null) previous.flush();
        current = this;
    }

    static HudGlyphBatch begin(DrawContext owner, String text) {
        if (text.length() < 2) return null;
        return new HudGlyphBatch(owner);
    }

    public static HudGlyphBatch beginShape(DrawContext owner) {
        return new HudGlyphBatch(owner);
    }

    public static boolean shouldSubmit(DrawContext owner, GuiRenderState state, SimpleGuiElementRenderState glyph) {
        var batch = current;
        if (batch == null || batch.owner != owner) return true;
        batch.add(state, glyph);
        return false;
    }

    private void add(GuiRenderState state, SimpleGuiElementRenderState glyph) {
        var bounds = glyph.bounds();
        if (bounds == null) return;
        if (glyphs != null) {
            var first = glyphs.getFirst();
            if (this.state != state || first.pipeline() != glyph.pipeline()
                    || !first.textureSetup().equals(glyph.textureSetup())
                    || !Objects.equals(first.scissorArea(), glyph.scissorArea())) flush();
        }
        if (glyphs == null) {
            glyphs = new ArrayList<>(8);
            this.state = state;
            left = bounds.getLeft();
            top = bounds.getTop();
            right = bounds.getRight();
            bottom = bounds.getBottom();
        } else {
            left = Math.min(left, bounds.getLeft());
            top = Math.min(top, bounds.getTop());
            right = Math.max(right, bounds.getRight());
            bottom = Math.max(bottom, bounds.getBottom());
        }
        glyphs.add(glyph);
    }

    void flush() {
        if (glyphs == null) return;
        var element = glyphs.size() == 1 ? glyphs.getFirst()
                : new GlyphRun(glyphs, new ScreenRect(left, top, right - left, bottom - top));
        state.addSimpleElement(element);
        glyphs = null;
        state = null;
    }

    @Override
    public void close() {
        try {
            flush();
        } finally {
            current = previous;
        }
    }

    private record GlyphRun(List<SimpleGuiElementRenderState> glyphs, ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vertices) {
            for (var glyph : glyphs) glyph.setupVertices(vertices);
        }

        @Override public RenderPipeline pipeline() { return glyphs.getFirst().pipeline(); }
        @Override public TextureSetup textureSetup() { return glyphs.getFirst().textureSetup(); }
        @Override public ScreenRect scissorArea() { return glyphs.getFirst().scissorArea(); }
    }
}
