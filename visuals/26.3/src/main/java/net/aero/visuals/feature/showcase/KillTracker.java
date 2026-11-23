package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import java.util.*;
/** Only locally attacked victims with an observed death; removal/unload is not a kill. */
final class KillTracker {
    private record Target(LivingEntity entity,long expiry){}
    private static final LinkedHashMap<UUID,Target> targets=new LinkedHashMap<>();private static Object world;
    private KillTracker(){}
    private static boolean active(){var visual=VisualExpansion.get(KillVisuals.class);var sound=VisualExpansion.get(KillSound.class);return visual!=null&&visual.isEnabled()||sound!=null&&sound.isEnabled();}
    static void attack(Entity entity){var mc=Minecraft.getInstance();if(!active()||mc.player==null||entity==mc.player||!(entity instanceof LivingEntity living)||!living.isAlive())return;if(world!=mc.level){targets.clear();world=mc.level;}targets.put(living.getUUID(),new Target(living,System.nanoTime()+4_000_000_000L));while(targets.size()>32)targets.remove(targets.keySet().iterator().next());}
    static void tick(Minecraft client) {
        if (!net.aero.visuals.guard.AeroGuard.allowed() || client.level == null
                || client.player == null || world != client.level || !active()) {
            targets.clear();
            world = null;
            return;
        }
        long now = System.nanoTime();
        var iterator = targets.values().iterator();
        while (iterator.hasNext()) {
            var target = iterator.next();
            var victim = target.entity();
            if (now - target.expiry() >= 0) {
                iterator.remove();
                continue;
            }
            if (victim.deathTime > 0 || victim.getHealth() <= 0 && !victim.isAlive()) {
                if (client.player.hasLineOfSight(victim)) {
                    var visual = VisualExpansion.get(KillVisuals.class);
                    if (visual != null && visual.isEnabled()) visual.death(victim.position());
                    var sound = VisualExpansion.get(KillSound.class);
                    if (sound != null && sound.isEnabled()) sound.play();
                }
                iterator.remove();
            } else if (victim.isRemoved()) {
                iterator.remove();
            }
        }
    }
}
