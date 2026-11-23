package net.aero.visuals.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Captures CPU vertices for deferred rendering, without retaining entities or the render context. */
public final class WorldGeometry {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    private WorldGeometry() {}

    public static Frame begin(LevelRenderContext context) {
        var frame = new Frame(context, ACTIVE.get());
        ACTIVE.set(frame);
        return frame;
    }

    public static Frame buffers(LevelRenderContext context) {
        Frame frame = ACTIVE.get();
        return frame != null && frame.context == context ? frame : null;
    }

    public static final class Frame {
        private final LevelRenderContext context;
        private final Frame parent;
        private final Map<RenderType, Recorder> layers = new LinkedHashMap<>();
        private int effect;

        private Frame(LevelRenderContext context, Frame parent) {
            this.context = context;
            this.parent = parent;
        }

        public void beginEffect() {
            effect++;
        }

        public VertexConsumer getBuffer(RenderType layer) {
            Recorder recorder = layers.computeIfAbsent(layer, ignored -> new Recorder());
            recorder.checkpoint(effect);
            return recorder;
        }

        public void rollbackEffect() {
            for (Recorder recorder : layers.values()) {
                if (recorder.effect == effect) recorder.rollback();
            }
        }

        public void finish(boolean submit) {
            if (ACTIVE.get() != this) throw new IllegalStateException("World geometry frames finished out of order");
            if (parent == null) ACTIVE.remove();
            else ACTIVE.set(parent);
            if (!submit) return;

            PoseStack identity = null;
            var collector = context.submitNodeCollector();
            for (var entry : layers.entrySet()) {
                Recorder recorder = entry.getValue();
                if (recorder.isEmpty()) continue;
                if (identity == null) identity = new PoseStack();
                // The collector copies the pose. Vertex pages belong to this submission until replay.
                recorder.seal();
                collector.submitCustomGeometry(identity, entry.getKey(), (pose, consumer) -> recorder.replay(consumer));
            }
        }
    }

    private static final class Recorder implements VertexConsumer {
        private static final int STRIDE = 14;
        private static final int FIRST_PAGE_VERTICES = 64;
        private static final int PAGE_VERTICES = 256;
        private static final int ONE = Float.floatToRawIntBits(1);

        private final List<int[]> pages = new ArrayList<>(1);
        private int[] page;
        private int base = -STRIDE;
        private int count;
        private int effect = -1;
        private int checkpointCount;

        void checkpoint(int currentEffect) {
            if (effect == currentEffect) return;
            effect = currentEffect;
            checkpointCount = count;
        }

        void rollback() {
            if (count == checkpointCount) return;
            count = checkpointCount;
            if (count == 0) {
                pages.clear();
                page = null;
                base = -STRIDE;
                return;
            }
            int pageIndex = count <= FIRST_PAGE_VERTICES ? 0
                    : 1 + (count - FIRST_PAGE_VERTICES - 1) / PAGE_VERTICES;
            int used = pageIndex == 0 ? count
                    : count - FIRST_PAGE_VERTICES - (pageIndex - 1) * PAGE_VERTICES;
            pages.subList(pageIndex + 1, pages.size()).clear();
            page = pages.get(pageIndex);
            base = (used - 1) * STRIDE;
            Arrays.fill(page, used * STRIDE, page.length, 0);
        }

        boolean isEmpty() {
            return count == 0;
        }

        void seal() {
            page = null;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (page == null && count != 0) throw new IllegalStateException("Geometry was already submitted");
            base += STRIDE;
            if (page == null || base == page.length) {
                page = new int[STRIDE * (count == 0 ? FIRST_PAGE_VERTICES : PAGE_VERTICES)];
                pages.add(page);
                base = 0;
            }
            count++;
            page[base] = Float.floatToRawIntBits(x);
            page[base + 1] = Float.floatToRawIntBits(y);
            page[base + 2] = Float.floatToRawIntBits(z);
            page[base + 3] = -1;
            page[base + 11] = ONE;
            page[base + 13] = ONE;
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        @Override public VertexConsumer setColor(int color) { page[base + 3] = color; return this; }
        @Override public VertexConsumer setUv(float u, float v) { page[base + 4] = Float.floatToRawIntBits(u); page[base + 5] = Float.floatToRawIntBits(v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { page[base + 6] = u; page[base + 7] = v; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { page[base + 8] = u; page[base + 9] = v; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { page[base + 10] = Float.floatToRawIntBits(x); page[base + 11] = Float.floatToRawIntBits(y); page[base + 12] = Float.floatToRawIntBits(z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { page[base + 13] = Float.floatToRawIntBits(width); return this; }

        private void replay(VertexConsumer out) {
            int remaining = count;
            for (int[] vertices : pages) {
                int used = Math.min(remaining, vertices.length / STRIDE);
                for (int p = 0; p < used * STRIDE; p += STRIDE) {
                    out.addVertex(Float.intBitsToFloat(vertices[p]), Float.intBitsToFloat(vertices[p + 1]), Float.intBitsToFloat(vertices[p + 2]))
                            .setColor(vertices[p + 3]).setUv(Float.intBitsToFloat(vertices[p + 4]), Float.intBitsToFloat(vertices[p + 5]))
                            .setUv1(vertices[p + 6], vertices[p + 7]).setUv2(vertices[p + 8], vertices[p + 9])
                            .setNormal(Float.intBitsToFloat(vertices[p + 10]), Float.intBitsToFloat(vertices[p + 11]), Float.intBitsToFloat(vertices[p + 12]))
                            .setLineWidth(Float.intBitsToFloat(vertices[p + 13]));
                }
                remaining -= used;
            }
        }
    }
}
