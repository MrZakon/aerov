package net.aero.visuals.render.performance;

import java.util.Arrays;

/** Bounded frame history. Snapshot work is kept off the per-frame sampling path. */
public final class FrameStatistics {
    private final double[] samples;
    private final double[] sorted;
    private int next, size;

    public FrameStatistics(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        samples = new double[capacity];
        sorted = new double[capacity];
    }

    public void add(long elapsedNanos) {
        if (elapsedNanos <= 0) return;
        samples[next] = elapsedNanos / 1_000_000.0;
        next = (next + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    public Snapshot snapshot() {
        if (size == 0) return Snapshot.EMPTY;
        double sum = 0, peak = 0;
        int stalls = 0;
        for (int i = 0; i < size; i++) {
            double sample = samples[i];
            sorted[i] = sample;
            sum += sample;
            peak = Math.max(peak, sample);
            if (sample > 50) stalls++;
        }
        Arrays.sort(sorted, 0, size);
        return new Snapshot(size, 1000 * size / sum, sum / size,
                sorted[(int) Math.ceil(size * .99) - 1], peak, stalls);
    }

    public void reset() { next = size = 0; }

    /** p99 is frame time in ms, not an average of instantaneous FPS values. */
    public record Snapshot(int frames, double fps, double meanMs, double p99Ms, double peakMs, int stalls) {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0);
    }
}
