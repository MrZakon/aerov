package net.aero.visuals.module.impl.misc;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.performance.EffectsBudgetController;

/** Applies transient render limits; never writes adaptive values into saved effect settings. */
public final class SmartEffectsModule extends Module {
    private static SmartEffectsModule active;
    private final DoubleSetting target = number("target_fps", "Желаемый FPS", 240, 30, 500, 10);
    private final DoubleSetting minimum = number("minimum_quality", "Минимальный бюджет (%)", 40, 20, 100, 5);
    private final BooleanSetting particles = flag("particles", "Адаптировать частицы", true);
    private final BooleanSetting postFx = flag("post_fx", "Адаптировать размытие и bloom", true);
    private final BooleanSetting cosmetics = flag("cosmetics", "Адаптировать дальнюю чужую косметику", true);
    private final BooleanSetting keepHits = flag("keep_hits", "Закрепить частицы попадания", false);
    private final EffectsBudgetController controller = new EffectsBudgetController();
    private double effectiveTarget = 240;

    public SmartEffectsModule() {
        super("smart_effects", "Умные эффекты", "Поддерживает целевой FPS за счёт эффектов Aero. Учитывает лимит FPS и VSync. Отключите адаптацию любимой группы, чтобы закрепить её качество.", ModuleCategory.RENDER);
    }
    @Override protected void onEnable() { controller.reset(); active = this; }
    @Override protected void onDisable() { if (active == this) active = null; controller.reset(); }
    public static SmartEffectsModule active() { return active != null && active.isEnabled() ? active : null; }
    public void sample(long nanos) { controller.sample(nanos, effectiveTarget, minimum.get() / 100); }
    public void pause() { controller.pause(); }
    public void resetBudget() { controller.reset(); }
    public void updateTarget(int cap, boolean vsync, int refresh) {
        effectiveTarget = EffectsBudgetController.effectiveTarget(target.get(), cap, vsync, refresh);
    }
    public double effectiveTarget() { return effectiveTarget; }
    public double factor() { return controller.factor(); }
    @Override public String displayValue() { return Math.round(factor() * 100) + "%"; }

    public static int particleCount(int requested, String effect) {
        var module = active();
        if (requested <= 0) return 0;
        if (module == null || !module.particles.get() || module.keepHits.get() && "hit_particles".equals(effect)) return requested;
        return Math.max(1, (int) Math.round(requested * module.factor()));
    }
    private static double postFxFactor() {
        var module = active();
        return module != null && module.postFx.get() ? module.factor() : 1;
    }
    public static float fogBlur(float requested) {
        return requested * (float) Math.clamp((postFxFactor() - .4) / .6, 0, 1);
    }
    public static int fogTaps(int requested) {
        double factor = postFxFactor();
        return Math.min(requested, factor < .55 ? 2 : factor < .8 ? 4 : 6);
    }
    public static boolean fastBloom() { return postFxFactor() < .8; }
    public static double cosmeticRangeSquared() {
        var module = active();
        if (module == null || !module.cosmetics.get() || module.factor() >= .999) return Double.POSITIVE_INFINITY;
        double range = Math.max(24, 128 * module.factor());
        return range * range;
    }
}
