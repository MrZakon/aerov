package net.aero.visuals.module;


import net.aero.visuals.module.impl.hud.TargetHudModule;
import net.aero.visuals.module.impl.hud.ArmorHudModule;
import net.aero.visuals.module.impl.hud.PotionsHudModule;
import net.aero.visuals.module.impl.hud.CooldownsHudModule;
import net.aero.visuals.module.impl.hud.KeybindsHudModule;
import net.aero.visuals.module.impl.hud.MediaPlayerModule;
import net.aero.visuals.module.impl.inventory.InventoryHudModule;
import net.aero.visuals.module.impl.inventory.ItemScrollerModule;
import net.aero.visuals.module.impl.inventory.ShulkerPreviewModule;
import net.aero.visuals.module.impl.inventory.LockSlotModule;
import net.aero.visuals.module.impl.misc.AutoCommandsModule;
import net.aero.visuals.module.impl.misc.IslandModule;
import net.aero.visuals.module.impl.pvp.TargetEspModule;
import net.aero.visuals.module.impl.pvp.CrosshairModule;
import net.aero.visuals.module.impl.pvp.HitSoundsModule;
import net.aero.visuals.module.impl.pvp.HitColorModule;
import net.aero.visuals.module.impl.pvp.PredictionModule;
import net.aero.visuals.module.impl.pvp.ViewModelModule;
import net.aero.visuals.module.impl.pvp.SwingAnimationModule;

public final class ModuleRegistry {
    private ModuleRegistry() {}
    public static void registerAll(ModuleManager modules) {
        registerAll(modules, new net.aero.visuals.config.ClientSettings());
    }
    public static void registerAll(ModuleManager modules, net.aero.visuals.config.ClientSettings settings) {

        modules.register(new InventoryHudModule());
        modules.register(new ItemScrollerModule());
        modules.register(new ShulkerPreviewModule());
        modules.register(new LockSlotModule());
        modules.register(new IslandModule());
        modules.register(new TargetHudModule());
        modules.register(new ArmorHudModule());
        modules.register(new PotionsHudModule());
        modules.register(new CooldownsHudModule());
        modules.register(new KeybindsHudModule());
        modules.register(new MediaPlayerModule());
        modules.register(new TargetEspModule());
        modules.register(new CrosshairModule());
        modules.register(new HitSoundsModule());
        modules.register(new HitColorModule());
        modules.register(new PredictionModule());
        modules.register(new ViewModelModule());
        modules.register(new SwingAnimationModule());
        modules.register(new net.aero.visuals.module.impl.pvp.AutoClickerModule());
        modules.register(new AutoCommandsModule());
        modules.register(new net.aero.visuals.alt.MultiAccountModule());
        modules.register(new net.aero.visuals.module.impl.misc.NightVisionModule());
        net.aero.visuals.feature.premium.PremiumFeatures.install(modules);
        net.aero.visuals.feature.ImportedFeatures.install(modules);
        net.aero.visuals.feature.ExtraFeatures.install(modules);
        net.aero.visuals.feature.enhanced.VisualExpansion.install(modules);
        modules.register(new net.aero.visuals.module.impl.hud.KeystrokesHudModule());
        modules.register(new net.aero.visuals.module.impl.hud.TotemsHudModule());
        modules.register(new net.aero.visuals.module.impl.hud.SaturationHudModule());
        modules.register(new net.aero.visuals.module.impl.hud.FrameTimeHudModule());
        modules.register(new net.aero.visuals.module.impl.hud.FpsInspectorModule());
        modules.register(new net.aero.visuals.module.impl.misc.SmartEffectsModule());
        modules.register(new net.aero.visuals.feature.utility.ChestTracker());
        modules.register(new net.aero.visuals.feature.utility.PairedEmotes());
        modules.register(new net.aero.visuals.module.impl.hud.CraftPlannerModule());
    }
}
