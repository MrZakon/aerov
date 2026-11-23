package net.aero.visuals.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTest {
    @Test
    void invalidDeltaDoesNotPoisonSpring() {
        SpringValue spring = new SpringValue(0.0, 190.0, 24.0);
        spring.setTarget(1);
        assertEquals(0.0, spring.update(Double.NaN));
        assertTrue(Double.isFinite(spring.update(1.0 / 60)));
    }

    @Test
    void pauseUsesBoundedIntegrationSteps() {
        SpringValue paused = new SpringValue(0.0, 190.0, 24.0);
        SpringValue capped = new SpringValue(0.0, 190.0, 24.0);
        paused.setTarget(1);
        capped.setTarget(1);
        assertEquals(capped.update(.1), paused.update(30), 1.0E-12);
    }

    @Test
    void springConvergesWithoutDiverging() {
        SpringValue spring = new SpringValue(0.0, 190.0, 24.0);
        spring.setTarget(1.0);
        for (int index = 0; index < 600; index++) {
            spring.update(1.0 / 120.0);
            assertTrue(Double.isFinite(spring.value()));
        }
        assertEquals(1.0, spring.value(), 1.0E-4);
    }
}
