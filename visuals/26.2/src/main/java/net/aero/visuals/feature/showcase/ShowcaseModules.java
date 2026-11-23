package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
/** Optional cosmetic modules. No donor combat logic, packets or menu replacements. */
public final class ShowcaseModules {
    private ShowcaseModules(){}
    public static void install(ModuleManager modules){
        modules.register(new KillVisuals());modules.register(new KillSound());
        modules.register(new Materials.Player());modules.register(new Materials.Crystals());
        modules.register(new DashTrail());modules.register(new AspectRatio());
        modules.register(new SelfNametags());modules.register(new ChunkAnimator());
    }
    public static void attack(Entity entity){KillTracker.attack(entity);}
    public static void tick(Minecraft client){KillTracker.tick(client);}
    public static void close(){var sound=VisualExpansion.get(KillSound.class);if(sound!=null)sound.close();}
}
