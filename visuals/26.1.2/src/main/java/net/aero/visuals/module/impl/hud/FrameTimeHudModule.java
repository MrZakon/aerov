package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.HudGlyphBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FrameTimeHudModule extends HudWidgetModule {
    private static final int CAPACITY = 120;
    private static FrameTimeHudModule recorder;
    private final double[] samples = new double[CAPACITY];
    private long previousFrame;
    private java.lang.ref.WeakReference<Object> sampledWorld;
    private int next;
    private int count;
    private double peak;
    private static final long SUMMARY_INTERVAL_NANOS = 250_000_000L;
    private long lastSummary;
    private String summary = "";
    private String summaryPeakLabel;

    public FrameTimeHudModule() {
        super("frame_time_hud", "График плавности", "Время каждого кадра в миллисекундах.", new HudPosition(HudAnchor.TOP_LEFT, 8, 105, 1));
    }

    public static void recordFrame(Minecraft client) {
        var hud = recorder;
        if (hud == null) return;
        var world = client.level;
        if (world == null || client.player == null) {
            hud.reset();
            return;
        }
        if (hud.sampledWorld == null || hud.sampledWorld.get() != world) {
            hud.reset();
            hud.sampledWorld = new java.lang.ref.WeakReference<>(world);
        }
        if (!client.isWindowActive() || client.getWindow().isIconified()
                || client.getOverlay() != null) {
            hud.previousFrame = 0;
            return;
        }
        long now = System.nanoTime();
        long elapsed = now - hud.previousFrame;
        if (hud.previousFrame != 0 && elapsed > 0) {
            hud.addSample(elapsed / 1_000_000.0);
        }
        hud.previousFrame = now;
    }

    private void addSample(double milliseconds) {
        boolean replacingPeak = count == CAPACITY && samples[next] == peak;
        samples[next] = milliseconds;
        next = (next + 1) % CAPACITY;
        if (count < CAPACITY) count++;

        if (milliseconds >= peak) {
            peak = milliseconds;
        } else if (replacingPeak) {
            peak = 0;
            for (double sample : samples) peak = Math.max(peak, sample);
        }
    }

    private void reset() {
        previousFrame = 0;
        sampledWorld = null;
        count = next = 0;
        peak = 0;
        lastSummary = 0;
        summary = "";
        summaryPeakLabel = null;
    }

    @Override protected void onEnable() { recorder = this; }
    @Override protected void onDisable() { if (recorder == this) recorder = null; reset(); }

    @Override
    protected void refresh(Minecraft client, float delta) {
        width = 162;
        height = 78;
    }

    @Override
    protected void renderHud(GuiGraphicsExtractor draw, Minecraft client) {
        title(draw, LocalText.pick("Плавность кадров", "Frame time", "Плавність кадрів"));
        if (count == 0) return;
        final int left = 9, bottom = 61, plotHeight = 34;
        int oldest = (next - count + CAPACITY) % CAPACITY;
        double ceiling = peak > 67 ? 100 : peak > 34 ? 67 : 34;
        int guide = bottom - (int) Math.round(plotHeight * 16.667 / ceiling);
        UiRenderer.rect(draw, left, guide, 144, 1, 0x508A968E);
        // Keep the columns in one GUI element so the plot is layered as a single shape.
        try (var batch = HudGlyphBatch.beginShape(draw)) {
            int firstX = left + 144 - count;
            int runX = firstX;
            int runHeight = 0;
            int runColor = 0;
            for (int index = 0; index < count; index++) {
                double ms = samples[(oldest + index) % CAPACITY];
                int height = Math.max(1, (int) Math.round(plotHeight * Math.min(ms, ceiling) / ceiling));
                int color = ms <= 16.667 ? 0xFF61D4A6 : ms <= 33.334 ? 0xFFFFC474 : 0xFFFF7979;
                int x = firstX + index;
                if (height != runHeight || color != runColor) {
                    if (x > runX) draw.fill(runX, bottom - runHeight, x, bottom, runColor);
                    runX = x;
                    runHeight = height;
                    runColor = color;
                }
            }
            draw.fill(runX, bottom - runHeight, firstX + count, bottom, runColor);
        }
        long now = System.nanoTime();
        String peakLabel = LocalText.pick("пик", "peak", "пік");
        if (!peakLabel.equals(summaryPeakLabel) || now - lastSummary >= SUMMARY_INTERVAL_NANOS) {
            lastSummary = now;
            summaryPeakLabel = peakLabel;
            double latest = samples[(next + CAPACITY - 1) % CAPACITY];
            summary = String.format(java.util.Locale.ROOT, "%.1f ms  ·  %s %.1f ms", latest, peakLabel, peak);
        }
        text(draw, summary, left, 65, 8, 0xFFE2E9EE);
    }
}
