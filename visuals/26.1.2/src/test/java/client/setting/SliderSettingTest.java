package client.setting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SliderSettingTest {
    @Test void preservesFractionalStepIncludingStepsAboveOne() {
        SliderSetting quarters = new SliderSetting("Test", "", 0, 0, 10, .25);
        quarters.setDouble2(2.25);
        assertEquals(2.25, quarters.getValue());
        assertEquals(2, quarters.getValue6());
        SliderSetting halves = new SliderSetting("Test", "", 0, 0, 10, 2.5);
        halves.setDouble2(2.5);
        assertEquals(2.5, halves.getValue());
        assertEquals(1, halves.getValue6());
    }
    @Test void alignsStepsToMinimumAndIgnoresInvalidValues() {
        SliderSetting slider = new SliderSetting("Test", "", 1, 1, 10, .5);
        slider.setDouble2(2.3);
        assertEquals(2.5, slider.getValue());
        slider.setDouble2(Double.NaN);
        assertEquals(2.5, slider.getValue());
    }
}
