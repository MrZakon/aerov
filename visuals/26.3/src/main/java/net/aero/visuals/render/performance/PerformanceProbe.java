package net.aero.visuals.render.performance;

import java.util.*;

/** Inclusive CPU submission time only; never a GPU timer or a claim about FPS saved. */
public final class PerformanceProbe {
    private static final class Counter { long nanos; }
    private static final Map<String, Counter> totals = new LinkedHashMap<>();
    private static Thread renderThread;
    private static long started;
    private static int frames;
    private static boolean recording;
    private static List<Cost> costs = List.of();
    private PerformanceProbe() {}

    public static void beginFrame(boolean enabled) {
        renderThread = enabled ? Thread.currentThread() : null;
        recording = enabled;
        if (!enabled) { totals.values().forEach(counter -> counter.nanos = 0); started = 0; frames = 0; }
        else if (started == 0) started = System.nanoTime();
    }
    public static long begin() {
        return recording && renderThread == Thread.currentThread() ? System.nanoTime() : 0;
    }
    public static void end(String label, long start) {
        if (start == 0 || !recording || renderThread != Thread.currentThread()) return;
        long elapsed = System.nanoTime() - start;
        // Module IDs/section names form a bounded set; labels must never contain per-frame values.
        Counter counter = totals.get(label);
        if (counter == null && totals.size() < 256) {
            counter = new Counter(); totals.put(label, counter);
        }
        if (counter != null) counter.nanos += elapsed;
    }
    public static void endFrame() {
        if (!recording || renderThread != Thread.currentThread()) return;
        frames++;
        long now = System.nanoTime();
        if (now - started >= 500_000_000L) {
            var result = new ArrayList<Cost>(totals.size());
            totals.forEach((name, counter) -> result.add(new Cost(name, counter.nanos / (frames * 1e6))));
            result.sort(Comparator.comparingDouble(Cost::milliseconds).reversed());
            costs = List.copyOf(result.subList(0, Math.min(4, result.size())));
            totals.values().forEach(counter -> counter.nanos = 0);
            frames = 0; started = now;
        }
        recording = false;
    }
    public static List<Cost> costs() { return costs; }
    public static void reset() {
        recording = false; renderThread = null; totals.clear();
        costs = List.of(); started = 0; frames = 0;
    }
    public record Cost(String label, double milliseconds) {}
}
