package net.aero.visuals.module.impl.misc;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;

public final class NightVisionModule extends Module {
    private final DoubleSetting strength = add(new DoubleSetting(
            "night_vision_strength", "Интенсивность", "Сила подсветки в темноте.", 100, 5, 100, 5));

    public NightVisionModule() {
        super("fullbright", "NightVision", "Подсвечивает тёмные участки мира.", ModuleCategory.RENDER);
    }

    public static float lightmapIntensity(float vanilla) {
        var runtime = ClientRuntime.getNullable();
        var module = runtime == null ? null : runtime.module(NightVisionModule.class);
        return module != null && module.isEnabled() ? Math.max(vanilla, module.strength.get().floatValue() / 100) : vanilla;
    }
}
