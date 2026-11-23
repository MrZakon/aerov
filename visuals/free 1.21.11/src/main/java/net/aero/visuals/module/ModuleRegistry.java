package net.aero.visuals.module;
import net.aero.visuals.module.impl.inventory.*;
import net.aero.visuals.module.impl.hud.*;
import net.aero.visuals.module.impl.pvp.*;
import net.aero.visuals.module.impl.misc.IslandModule;
import net.aero.visuals.feature.ImportedFeatures;
/** An explicit registration list: paid presets cannot instantiate additional modules. */
public final class ModuleRegistry {
    private ModuleRegistry() {}
    public static void registerAll(ModuleManager modules){registerAll(modules,new net.aero.visuals.config.ClientSettings());}
    public static void registerAll(ModuleManager modules,net.aero.visuals.config.ClientSettings settings){
        modules.register(new InventoryHudModule()); modules.register(new ShulkerPreviewModule());
        modules.register(new ArmorHudModule()); modules.register(new PotionsHudModule());
        modules.register(new CrosshairModule()); modules.register(new HitSoundsModule());
        modules.register(new KeystrokesHudModule());
        modules.register(new TotemsHudModule());
        modules.register(new SaturationHudModule());
        modules.register(new KeybindsHudModule());
        ImportedFeatures.install(modules);
        modules.register(new IslandModule());
    }
}
