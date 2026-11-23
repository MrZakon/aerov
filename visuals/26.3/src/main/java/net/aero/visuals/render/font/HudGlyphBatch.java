package net.aero.visuals.render.font;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.render.TextureSetup;

/** Groups consecutive quads that share the same pipeline, texture, and clip into one GUI element. */
public final class HudGlyphBatch implements AutoCloseable {
    private static HudGlyphBatch current;
    private final GuiGraphicsExtractor owner;
    private final HudGlyphBatch previous;
    private GuiRenderState state;
    private List<GuiElementRenderState> glyphs;
    private int left, top, right, bottom;

    private HudGlyphBatch(GuiGraphicsExtractor owner) {
        this.owner = owner;
        previous = current;
        if (previous != null) previous.flush();
        current = this;
    }

    static HudGlyphBatch begin(GuiGraphicsExtractor owner, String text) {
        if (text.length() < 2) return null;
        return new HudGlyphBatch(owner);
    }

    public static HudGlyphBatch beginShape(GuiGraphicsExtractor owner) {
        return new HudGlyphBatch(owner);
    }

    public static boolean shouldSubmit(GuiGraphicsExtractor owner, GuiRenderState state, GuiElementRenderState glyph) {
        var batch = current;
        if (batch == null || batch.owner != owner) return true;
        batch.add(state, glyph);
        return false;
    }

    private void add(GuiRenderState state, GuiElementRenderState glyph) {
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
            left = bounds.left();
            top = bounds.top();
            right = bounds.right();
            bottom = bounds.bottom();
        } else {
            left = Math.min(left, bounds.left());
            top = Math.min(top, bounds.top());
            right = Math.max(right, bounds.right());
            bottom = Math.max(bottom, bounds.bottom());
        }
        glyphs.add(glyph);
    }

    void flush() {
        if (glyphs == null) return;
        var element = glyphs.size() == 1 ? glyphs.getFirst()
                : new GlyphRun(glyphs, new ScreenRectangle(left, top, right - left, bottom - top));
        state.addGuiElement(element);
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

    private record GlyphRun(List<GuiElementRenderState> glyphs, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vertices) {
            for (var glyph : glyphs) glyph.buildVertices(vertices);
        }

        @Override public RenderPipeline pipeline() { return glyphs.getFirst().pipeline(); }
        @Override public TextureSetup textureSetup() { return glyphs.getFirst().textureSetup(); }
        @Override public ScreenRectangle scissorArea() { return glyphs.getFirst().scissorArea(); }
    }
}
