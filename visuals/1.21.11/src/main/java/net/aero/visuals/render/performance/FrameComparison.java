package net.aero.visuals.render.performance;

/** Two explicit, equal-duration measurements. Menus/focus loss pause sampling at the caller. */
public final class FrameComparison {
    public enum Stage { IDLE, WARMUP_BEFORE, BEFORE, READY, WARMUP_AFTER, AFTER, COMPLETE }
    private static final long WARMUP = 2_000_000_000L, DURATION = 10_000_000_000L;
    // A fixed histogram covers every measured frame, even at very high FPS. 0.1 ms bins.
    private final long[] histogram = new long[4096];
    // A 10-second run can contain at most 25 frames >=409.5 ms, including its final frame.
    private final double[] longFrames = new double[32];
    private int longFrameCount;
    private Stage stage = Stage.IDLE;
    private long elapsed, frames, stalls;
    private double peakMs;
    private Result before, after;

    public void startBefore() { before = after = null; begin(Stage.WARMUP_BEFORE); }
    public boolean startAfter() {
        if (before == null || measuring()) return false;
        after = null;
        begin(Stage.WARMUP_AFTER);
        return true;
    }
    private void begin(Stage next) {
        stage = next;
        elapsed = frames = stalls = 0;
        peakMs = 0;
        longFrameCount = 0;
        java.util.Arrays.fill(histogram, 0);
    }
    public void sample(long nanos) {
        if (nanos <= 0 || !measuring()) return;
        elapsed += nanos;
        if (stage == Stage.WARMUP_BEFORE || stage == Stage.WARMUP_AFTER) {
            if (elapsed >= WARMUP) begin(stage == Stage.WARMUP_BEFORE ? Stage.BEFORE : Stage.AFTER);
            return;
        }
        double ms = nanos / 1_000_000.0;
        frames++;
        if (ms > 50) stalls++;
        peakMs = Math.max(peakMs, ms);
        int sampleBin = Math.min(histogram.length - 1, (int) (ms * 10));
        histogram[sampleBin]++;
        if (sampleBin == histogram.length - 1) longFrames[longFrameCount++] = ms;
        if (elapsed >= DURATION) {
            long needed = (long) Math.ceil(frames * .99), total = 0;
            int bin = 0;
            while (bin < histogram.length - 1 && (total += histogram[bin]) < needed) bin++;
            double p99;
            if (bin == histogram.length - 1) {
                java.util.Arrays.sort(longFrames, 0, longFrameCount);
                p99 = longFrames[(int) (needed - total - 1)];
            } else p99 = (bin + 1) / 10.0;
            var result = new Result(frames, elapsed, frames * 1e9 / elapsed, p99, peakMs, stalls);
            if (stage == Stage.BEFORE) { before = result; stage = Stage.READY; }
            else { after = result; stage = Stage.COMPLETE; }
        }
    }
    public boolean measuring() {
        return stage == Stage.WARMUP_BEFORE || stage == Stage.BEFORE
                || stage == Stage.WARMUP_AFTER || stage == Stage.AFTER;
    }
    public Stage stage() { return stage; }
    public Result before() { return before; }
    public Result after() { return after; }
    public double remainingSeconds() {
        long duration = stage == Stage.WARMUP_BEFORE || stage == Stage.WARMUP_AFTER ? WARMUP : DURATION;
        return measuring() ? Math.max(0, (duration - elapsed) / 1e9) : 0;
    }
    public void reset() { before = after = null; begin(Stage.IDLE); }
    public record Result(long frames, long durationNanos, double fps, double p99Ms, double peakMs, long stalls) {}
}
