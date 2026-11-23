package client.module;

import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AeroModuleAdapterTest {
    enum Mode { FIRST, SECOND }
    static class Source extends net.aero.visuals.module.Module {
        final BooleanSetting flag = add(new BooleanSetting("flag", "Flag", "", false));
        final DoubleSetting amount = add(new DoubleSetting("amount", "Amount", "", 1, 0, 10, .25));
        final ColorSetting color = add(new ColorSetting("color", "Color", "",
                new ColorValue(0xFF112233, 0xFF445566, true, 2)));
        final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Mode", "", Mode.FIRST, Mode.class));
        Source() { super("adapter_test", "Adapter test", "", ModuleCategory.MISC); }
    }

    @Test void menuChangesReachUnderlyingSettings() {
        Source source = new Source();
        var adapter = new AeroModuleAdapter(source, null);
        ((client.setting.BooleanSetting) adapter.getSettingByName("Flag")).setBoolean(true);
        ((client.setting.SliderSetting) adapter.getSettingByName("Amount")).setDouble2(2.5);
        ((client.setting.ListSetting) adapter.getSettingByName("Mode")).onString3("SECOND");
        assertTrue(source.flag.get());
        assertEquals(2.5, source.amount.get());
        assertEquals(Mode.SECOND, source.mode.get());
    }

    @Test void colorRoundTripPreservesAnimationAndSecondaryColor() {
        Source source = new Source();
        var adapter = new AeroModuleAdapter(source, null);
        var color = (client.setting.ColorSetting) adapter.getSettingByName("Color");
        color.setInt(0xFFABCDEF);
        assertEquals(new ColorValue(0xFFABCDEF, 0xFF445566, true, 2), source.color.get());
        ColorValue restored = new ColorValue(0xFF102030, 0xFF607080, false, .5);
        source.color.set(restored);
        assertEquals(restored.primaryArgb(), color.getInt2());
        assertEquals(restored, source.color.get());
    }

    @Test void conditionalVisibilityFollowsSource() {
        Source source = new Source();
        source.amount.visibleWhen(source.flag::get);
        var adapter = new AeroModuleAdapter(source, null);
        assertFalse(adapter.getSettingByName("Amount").isVisible());
        source.flag.set(true);
        assertTrue(adapter.getSettingByName("Amount").isVisible());
    }
}
