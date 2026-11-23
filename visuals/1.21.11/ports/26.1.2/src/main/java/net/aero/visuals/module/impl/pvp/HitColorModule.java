package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;

public final class HitColorModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Цвет попадания", "Цвет подсветки сущности при получении урона.", ColorValue.solid(0xFF61DDF2)));

    public HitColorModule() {
        super("hit_color", "HitColor", "Меняет цвет подсветки сущностей во время получения урона.", ModuleCategory.PVP);
    }

    public int color() { return color.get().eval(); }
}
