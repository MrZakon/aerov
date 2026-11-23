package net.aero.visuals.render.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceProbeTest {
    @Test void disabledProbeDoesNoTimingAndOtherThreadsCannotCaptureRenderWork() throws Exception {
        PerformanceProbe.beginFrame(false);
        assertEquals(0, PerformanceProbe.begin());
        PerformanceProbe.beginFrame(true);
        assertTrue(PerformanceProbe.begin() > 0);
        long[] backgroundToken = {-1};
        var thread = new Thread(() -> backgroundToken[0] = PerformanceProbe.begin());
        thread.start(); thread.join();
        assertEquals(0, backgroundToken[0]);
        PerformanceProbe.endFrame();
        assertEquals(0, PerformanceProbe.begin());
        PerformanceProbe.beginFrame(false);
        assertTrue(PerformanceProbe.costs().isEmpty());
    }
}
