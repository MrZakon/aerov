package net.aero.visuals.module.setting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoubleSettingTest {
    @Test
    void rejectsNonFiniteStepAndDefault() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DoubleSetting("test", "Test", "", 1, 0, 10, invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> new DoubleSetting("test", "Test", "", invalid, 0, 10, 1));
        }
    }
}
