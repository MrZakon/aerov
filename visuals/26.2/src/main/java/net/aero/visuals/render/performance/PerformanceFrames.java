package net.aero.visuals.render.performance;

import net.aero.visuals.module.impl.hud.FpsInspectorModule;
import net.aero.visuals.module.impl.misc.SmartEffectsModule;
import net.minecraft.client.Minecraft;
import java.lang.ref.WeakReference;

/** Shared frame gate for inspection and adaptation. No game/world strong references survive a frame. */
public final class PerformanceFrames {
    private static WeakReference<Object> world;
    private static long previous, settingsAt;
    private static boolean wasActive;
    private PerformanceFrames() {}

    public static void begin(Minecraft client) {
        var inspector = FpsInspectorModule.active();
        var adaptive = SmartEffectsModule.active();
        if (inspector == null && adaptive == null) {
            if (wasActive) { world = null; previous = settingsAt = 0; PerformanceProbe.reset(); }
            wasActive = false;
            return;
        }
        wasActive = true;
        Object current = client.level;
        if (world == null ? current != null : world.get() != current) {
            boolean changed = world != null;
            world = current == null ? null : new WeakReference<>(current);
            previous = settingsAt = 0;
            if (changed && inspector != null) inspector.resetMeasurements();
            if (adaptive != null) adaptive.resetBudget();
        }
        boolean gameplay = current != null && client.player != null && client.gui.screen() == null
                && client.isWindowActive() && !client.getWindow().isIconified()
                && client.gui.overlay() == null && !client.isPaused();
        PerformanceProbe.beginFrame(gameplay && inspector != null);
        if (!gameplay) {
            previous = 0;
            if (adaptive != null) adaptive.pause();
            return;
        }
        long now = System.nanoTime();
        if (adaptive != null && (settingsAt == 0 || now - settingsAt >= 500_000_000L)) {
            settingsAt = now;
            adaptive.updateTarget(client.options.framerateLimit().get(), client.options.enableVsync().get(), client.getWindow().getRefreshRate());
        }
        if (previous != 0 && now > previous) {
            long elapsed = now - previous;
            if (inspector != null) inspector.sample(elapsed);
            if (adaptive != null) adaptive.sample(elapsed);
        }
        previous = now;
    }
}
