package net.aero.visuals.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Utility for drawing lines adhering to Minecraft 1.21.11's VertexFormats.LINES requirement
 * (Position, Color, Normal, LineWidth).
 */
public final class LineRenderer {
    private LineRenderer() {}

    public static void line(MatrixStack.Entry entry, VertexConsumer lines,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float r, float g, float b, float a,
                            float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-5f) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dy = 1.0f;
        }

        lines.vertex(entry, x1, y1, z1)
                .color(r, g, b, a)
                .normal(entry, dx, dy, dz)
                .lineWidth(width);

        lines.vertex(entry, x2, y2, z2)
                .color(r, g, b, a)
                .normal(entry, dx, dy, dz)
                .lineWidth(width);
    }

    public static void line(MatrixStack.Entry entry, VertexConsumer lines,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            int argb) {
        float a = ((argb >> 24) & 0xFF) / 255.0f;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        line(entry, lines, x1, y1, z1, x2, y2, z2, r, g, b, a, 1.0f);
    }

    public static void renderLine(MatrixStack matrices, VertexConsumer lines,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2, int argb) {
        if (matrices != null && lines != null) {
            line(matrices.peek(), lines, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, argb);
        }
    }
}