package net.aero.visuals.feature.enhanced;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.setting.*;

/** Adapted visual ideas from the local Arbuz, Rival, Pulse and Linken sources.
 * Native Aero modules: no donor event buses, menus, combat or movement logic. */
public final class VisualExpansion {
    private VisualExpansion() {}
    public static void install(ModuleManager modules) {
        modules.register(new SwordTrail());
        modules.register(new HitParticles());
        modules.register(new TotemAnimation());
        modules.register(new BetterF5());
        modules.register(new VisualHud.Hotbar());

        modules.register(new Waypoints());
        modules.register(new ItemHighlighter());
        net.aero.visuals.feature.showcase.ShowcaseModules.install(modules);
    }
    public static <T extends Module> T get(Class<T> type) {
        var runtime=ClientRuntime.getNullable();return runtime==null?null:runtime.module(type);
    }
    public abstract static class Feature extends Module {
        protected Feature(String id,String name,String description,ModuleCategory category){super(id,name,description,category);}
    }
}
