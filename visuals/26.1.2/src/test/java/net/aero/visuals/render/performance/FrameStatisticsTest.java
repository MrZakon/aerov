package net.aero.visuals.render.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameStatisticsTest {
    @Test void fpsUsesTotalDurationAndP99IncludesStalls() {
        var stats = new FrameStatistics(100);
        for (int i = 0; i < 98; i++) stats.add(2_000_000);
        stats.add(60_000_000); stats.add(100_000_000);
        var result = stats.snapshot();
        assertEquals(100_000 / 356.0, result.fps(), .0001);
        assertEquals(60, result.p99Ms());
        assertEquals(100, result.peakMs());
        assertEquals(2, result.stalls());
    }
    @Test void boundedHistoryEvictsOldSpikesAndResets() {
        var stats = new FrameStatistics(2);
        stats.add(100_000_000); stats.add(2_000_000); stats.add(4_000_000);
        assertEquals(2, stats.snapshot().frames());
        assertEquals(4, stats.snapshot().peakMs());
        assertEquals(1000 / 3.0, stats.snapshot().fps(), .0001);
        stats.add(-1);
        assertEquals(2, stats.snapshot().frames());
        stats.reset();
        assertEquals(FrameStatistics.Snapshot.EMPTY, stats.snapshot());
    }
}
