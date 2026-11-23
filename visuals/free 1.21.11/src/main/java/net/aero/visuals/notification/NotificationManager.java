package net.aero.visuals.notification;

import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.DrawContext;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Capsule toast notification system matching Screenshot 2 (Yarn 1.21.11).
 */
public final class NotificationManager {
    private static final CopyOnWriteArrayList<Toast> ACTIVE_TOASTS = new CopyOnWriteArrayList<>();
    private static boolean initialized = false;

    private NotificationManager() {}

    public static void setInitialized(boolean init) {
        initialized = init;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void post(String text, boolean success) {
        while (ACTIVE_TOASTS.size() >= 4) {
            ACTIVE_TOASTS.remove(0);
        }
        ACTIVE_TOASTS.add(new Toast(text, success, System.currentTimeMillis()));
    }

    public static void render(DrawContext context, double x, double y, double scale) {
        long now = System.currentTimeMillis();
        Iterator<Toast> iter = ACTIVE_TOASTS.iterator();

        double currentY = y;
        while (iter.hasNext()) {
            Toast toast = iter.next();
            long elapsed = now - toast.startTime;
            if (elapsed > 2600) {
                ACTIVE_TOASTS.remove(toast);
                continue;
            }

            double alpha = 1.0;
            double offsetY = 0.0;
            if (elapsed < 200) {
                double progress = elapsed / 200.0;
                alpha = progress;
                offsetY = (1.0 - progress) * -8.0;
            } else if (elapsed > 2100) {
                double progress = (elapsed - 2100) / 500.0;
                alpha = Math.max(0.0, 1.0 - progress);
                offsetY = progress * -6.0;
            }

            renderToast(context, x, currentY + offsetY, toast.text, toast.success, alpha);
            currentY += 28.0;
        }
    }

    public static void renderPreview(DrawContext context, double x, double y) {
        renderToast(context, x, y, "Hit Particles выключен", false, 1.0);
    }

    private static void renderToast(DrawContext context, double x, double y, String text, boolean success, double alpha) {
        int a = (int) (alpha * 255);
        if (a <= 5) return;

        double textW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, text, 8.0);
        double w = 5.0 + 14.0 + 6.0 + textW + 10.0;
        double h = 24.0;

        int bg = ColorUtil.withAlpha(0xFF050608, (int) (a * 0.96));
        int border = ColorUtil.withAlpha(0xFFFFFFFF, (int) (a * 0.12));

        // Pill background
        UiRenderer.roundedRect(context, x, y, w, h, 12.0, bg);
        UiRenderer.roundedBorder(context, x, y, w, h, 12.0, 1.0, border);

        // Circular badge (diameter 14.0, radius 7.0 at x + 5, y + 5)
        double circleX = x + 12.0;
        double circleY = y + 12.0;
        int badgeColor = success ? ColorUtil.withAlpha(0xFF16A34A, a) : ColorUtil.withAlpha(0xFFDC2626, a);
        UiRenderer.circle(context, circleX, circleY, 7.0, badgeColor);

        // Inner icon: "✔" or "✕"
        int iconCol = ColorUtil.withAlpha(0xFFFFFFFF, a);
        if (success) {
            UiRenderer.font(context, FontFace.GOLOS_SEMI, "✔", circleX - 3.5, circleY - 4.0, 7.0, iconCol);
        } else {
            UiRenderer.font(context, FontFace.GOLOS_SEMI, "✕", circleX - 3.0, circleY - 4.5, 7.5, iconCol);
        }

        // Message text
        int textCol = ColorUtil.withAlpha(0xFFFFFFFF, a);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, text, x + 25.0, y + 6.5, 8.0, textCol);
    }

    public static int count() {
        return ACTIVE_TOASTS.size();
    }

    public static Toast getActiveToast() {
        long now = System.currentTimeMillis();
        for (int i = ACTIVE_TOASTS.size() - 1; i >= 0; i--) {
            Toast t = ACTIVE_TOASTS.get(i);
            if (now - t.startTime <= 2600) {
                return t;
            }
        }
        return null;
    }

    public record Toast(String text, boolean success, long startTime) {
        public long elapsed() {
            return System.currentTimeMillis() - startTime;
        }

        public boolean isExpired() {
            return elapsed() > 2600;
        }

        public double alpha() {
            long el = elapsed();
            if (el < 200) {
                return el / 200.0;
            } else if (el > 2100) {
                return Math.max(0.0, 1.0 - (el - 2100) / 500.0);
            }
            return 1.0;
        }
    }
}
