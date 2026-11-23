package net.aero.visuals.notification;

import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Capsule toast notification system matching Screenshot 2 (Yarn 1.21.11).
 */
public final class NotificationManager {
    private static final int MAX_TOASTS = 4;
    private static final long LIFETIME_MS = 2600;
    private static final Object TOAST_LOCK = new Object();
    private static volatile List<ActiveToast> activeToasts = List.of();
    private static volatile boolean initialized = false;
    private static volatile int resourceRevision;

    private NotificationManager() {}

    public static void onResourcesReloaded() {
        resourceRevision++;
    }

    public static void setInitialized(boolean init) {
        initialized = init;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void post(String text, boolean success) {
        if (text == null || text.isBlank()) return;
        net.aero.visuals.feature.FeatureJournal.record(text);
        synchronized (TOAST_LOCK) {
            long now = System.nanoTime();
            List<ActiveToast> current = activeToasts;
            int start = Math.max(0, current.size() - (MAX_TOASTS - 1));
            List<ActiveToast> updated = new ArrayList<>(MAX_TOASTS);
            for (int index = start; index < current.size(); index++) {
                ActiveToast active = current.get(index);
                if (!active.toast.isExpiredAt(now)) updated.add(active);
            }
            updated.add(new ActiveToast(new Toast(text, success, now)));
            activeToasts = List.copyOf(updated);
        }
    }

    public static void render(DrawContext context) {
        long now = System.nanoTime();
        List<ActiveToast> current = liveToasts(now);
        if (current.isEmpty()) return;
        int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
        int room = screenWidth - 16 - 35;
        int visible = Math.min(current.size(), Math.max(0, (screenHeight - 40) / 28));
        if (room <= 0 || visible == 0) return;
        double currentY = screenHeight - 32.0 - visible * 28.0;
        for (int index = current.size() - visible; index < current.size(); index++) {
            ActiveToast active = current.get(index);
            Toast toast = active.toast;
            long elapsed = toast.elapsedAt(now);

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

            if (alpha * 255 > 5) {
                var layout = active.layout(room);
                renderToast(context, screenWidth - 8 - layout.width(), Math.max(0, currentY + offsetY),
                        layout.text(), toast.success, alpha, layout.width());
            }
            currentY += 28.0;
        }
    }

    public static void renderPreview(DrawContext context, double x, double y) {
        String text = "Hit Particles выключен";
        renderToast(context, x, y, text, false, 1.0, 35 + UiRenderer.fontWidth(FontFace.GOLOS_SEMI, text, 8));
    }

    private static void renderToast(DrawContext context, double x, double y, String text, boolean success, double alpha, double w) {
        int a = (int) (alpha * 255);
        if (a <= 5) return;

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
        return liveToasts(System.nanoTime()).size();
    }

    public static Toast getActiveToast() {
        List<ActiveToast> current = liveToasts(System.nanoTime());
        return current.isEmpty() ? null : current.getLast().toast;
    }

    private static List<ActiveToast> liveToasts(long now) {
        List<ActiveToast> current = activeToasts;
        if (current.isEmpty() || !current.getFirst().toast.isExpiredAt(now)) return current;
        synchronized (TOAST_LOCK) {
            current = activeToasts;
            int first = 0;
            while (first < current.size() && current.get(first).toast.isExpiredAt(now)) first++;
            if (first > 0) activeToasts = List.copyOf(current.subList(first, current.size()));
            return activeToasts;
        }
    }

    private record Layout(int room, int revision, String text, double width) {}

    private static final class ActiveToast {
        private final Toast toast;
        // Layout is used only by the render thread.
        private Layout cachedLayout;

        private ActiveToast(Toast toast) {
            this.toast = toast;
        }

        private Layout layout(int room) {
            int revision = resourceRevision;
            Layout layout = cachedLayout;
            if (layout == null || layout.room() != room || layout.revision() != revision) {
                String text = UiRenderer.fitFont(FontFace.GOLOS_SEMI, toast.text(), room, 8, true);
                double width = 35 + UiRenderer.fontWidth(FontFace.GOLOS_SEMI, text, 8);
                layout = new Layout(room, revision, text, width);
                cachedLayout = layout;
            }
            return layout;
        }
    }

    public record Toast(String text, boolean success, long startTime) {
        private long elapsedAt(long now) {
            return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(now - startTime));
        }

        private boolean isExpiredAt(long now) {
            return elapsedAt(now) >= LIFETIME_MS;
        }

        public long elapsed() {
            return elapsedAt(System.nanoTime());
        }

        public boolean isExpired() {
            return isExpiredAt(System.nanoTime());
        }

        public double alpha() {
            long elapsed = elapsed();
            if (elapsed < 200) return elapsed / 200.0;
            if (elapsed > 2100) return Math.max(0.0, 1.0 - (elapsed - 2100) / 500.0);
            return 1.0;
        }
    }
}
