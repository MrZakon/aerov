package net.aero.visuals.render.performance;

/** Time-based feedback: no dependence on render FPS, asymmetric recovery and hysteresis. */
public final class EffectsBudgetController {
    private double factor = 1, elapsed, frames, pressure, headroom, lastTarget;

    public void sample(long nanos, double targetFps, double minimum) {
        if (nanos <= 0 || !Double.isFinite(targetFps) || targetFps <= 0) return;
        minimum = Double.isFinite(minimum) ? Math.clamp(minimum, .2, 1) : .4;
        factor = Math.max(factor, minimum);
        if (targetFps != lastTarget) { pause(); lastTarget = targetFps; }
        elapsed += nanos / 1e9;
        frames++;
        if (elapsed < .5) return;
        double seconds = elapsed, fps = frames / elapsed;
        elapsed = frames = 0;
        if (fps < targetFps * .90) {
            pressure += seconds;
            headroom = 0;
            if (pressure >= 1.5) { factor = Math.max(minimum, factor - .1); pressure = 0; }
        } else if (fps >= targetFps * .98) {
            headroom += seconds;
            pressure = 0;
            if (headroom >= 4) { factor = Math.min(1, factor + .025); headroom = 0; }
        } else { pressure = headroom = 0; }
    }
    /** Drop partial windows at pauses, but preserve the user's current quality level. */
    public void pause() { elapsed = frames = pressure = headroom = 0; }
    public void reset() { factor = 1; lastTarget = 0; pause(); }
    public double factor() { return factor; }

    public static double effectiveTarget(double requested, int cap, boolean vsync, int refreshRate) {
        double target = requested;
        // Minecraft stores the maximum slider value (260) as unlimited in all supported versions.
        if (cap > 0 && cap < 260) target = Math.min(target, cap);
        if (vsync && refreshRate > 0) target = Math.min(target, refreshRate);
        return target;
    }
}
