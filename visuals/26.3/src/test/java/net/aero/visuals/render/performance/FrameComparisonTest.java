package net.aero.visuals.render.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameComparisonTest {
    private static void seconds(FrameComparison comparison, int seconds, long frameNanos) {
        for (long n = 0; n < seconds * 1_000_000_000L; n += frameNanos) comparison.sample(frameNanos);
    }
    @Test void explicitSeparateRunsExcludeWarmupAndHaveEqualDuration() {
        var comparison = new FrameComparison();
        assertFalse(comparison.startAfter());
        comparison.startBefore();
        seconds(comparison, 2, 100_000_000);
        assertEquals(FrameComparison.Stage.BEFORE, comparison.stage());
        seconds(comparison, 10, 5_000_000);
        assertEquals(FrameComparison.Stage.READY, comparison.stage());
        assertEquals(200, comparison.before().fps());
        assertEquals(10_000_000_000L, comparison.before().durationNanos());
        assertTrue(comparison.startAfter());
        assertFalse(comparison.startAfter());
        seconds(comparison, 2, 100_000_000);
        seconds(comparison, 10, 10_000_000);
        assertEquals(FrameComparison.Stage.COMPLETE, comparison.stage());
        assertEquals(100, comparison.after().fps());
        assertEquals(10_000_000_000L, comparison.after().durationNanos());
        assertTrue(comparison.after().p99Ms() >= 10 && comparison.after().p99Ms() <= 10.1);
    }
    @Test void resetDiscardsBothRunsAndHighFpsDoesNotLoseSamples() {
        var comparison = new FrameComparison(); comparison.startBefore();
        seconds(comparison, 2, 100_000);
        seconds(comparison, 10, 100_000);
        assertEquals(100_000, comparison.before().frames());
        assertEquals(10_000, comparison.before().fps());
        comparison.reset();
        assertNull(comparison.before()); assertNull(comparison.after());
        assertFalse(comparison.measuring()); assertFalse(comparison.startAfter());
    }
    @Test void p99DoesNotBecomeTheWorstFrameWhenSeveralFramesExceedHistogramRange() {
        var comparison = new FrameComparison(); comparison.startBefore();
        seconds(comparison, 2, 100_000_000);
        for (int i = 0; i < 98; i++) comparison.sample(1_000_000);
        comparison.sample(1_000_000_000L);
        comparison.sample(8_000_000_000L);
        comparison.sample(2_000_000_000L);
        assertEquals(FrameComparison.Stage.READY, comparison.stage());
        assertEquals(2000, comparison.before().p99Ms());
        assertEquals(8000, comparison.before().peakMs());
    }
}
