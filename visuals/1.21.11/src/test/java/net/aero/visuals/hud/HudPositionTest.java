package net.aero.visuals.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudPositionTest {
    @Test
    void replacesNonFiniteCoordinatesAndScale() {
        HudPosition position = new HudPosition(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN);
        assertEquals(HudAnchor.TOP_LEFT, position.anchor());
        assertEquals(8.0, position.x());
        assertEquals(8.0, position.y());
        assertEquals(1.0, position.scale());
    }

    @Test
    void clampsFiniteValues() {
        HudPosition position = new HudPosition(HudAnchor.TOP_LEFT, -9000, 9000, 10);
        assertEquals(-8192.0, position.x());
        assertEquals(8192.0, position.y());
        assertEquals(4.0, position.scale());
    }
}
