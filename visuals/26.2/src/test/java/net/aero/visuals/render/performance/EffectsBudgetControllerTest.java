package net.aero.visuals.render.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectsBudgetControllerTest {
    private static void run(EffectsBudgetController controller, int fps, int seconds, double target, double minimum) {
        for (int i = 0; i < fps * seconds; i++) controller.sample(1_000_000_000L / fps, target, minimum);
    }
    @Test void targetHonorsExplicitCapAndVsyncRefresh() {
        assertEquals(70, EffectsBudgetController.effectiveTarget(240, 70, false, 144));
        assertEquals(144, EffectsBudgetController.effectiveTarget(240, 260, true, 144));
        assertEquals(240, EffectsBudgetController.effectiveTarget(240, 0, false, 60));
        assertEquals(500, EffectsBudgetController.effectiveTarget(500, 260, false, 144));
    }
    @Test void lowFpsReducesQualityWithoutCrossingFloorAndResetRestores() {
        var controller = new EffectsBudgetController();
        run(controller, 100, 1, 240, .4);
        assertEquals(1, controller.factor());
        run(controller, 100, 30, 240, .4);
        assertEquals(.4, controller.factor(), .00001);
        controller.reset(); assertEquals(1, controller.factor());
    }
    @Test void intentionalCapDoesNotDriveQualityDownAndRecoveryWorksAtCap() {
        var controller = new EffectsBudgetController();
        run(controller, 50, 8, 100, .4);
        double reduced = controller.factor(); assertTrue(reduced < 1);
        run(controller, 100, 20, 100, .4);
        assertTrue(controller.factor() > reduced);
        assertTrue(controller.factor() <= 1);
    }
    @Test void interruptedWindowsDoNotAccumulatePressureAndInvalidSamplesAreIgnored() {
        var controller = new EffectsBudgetController();
        for (int i = 0; i < 10; i++) { run(controller, 50, 1, 240, .4); controller.pause(); }
        assertEquals(1, controller.factor());
        controller.sample(1, Double.NaN, .4);
        controller.sample(-1, 240, .4);
        assertEquals(1, controller.factor());
    }
}
