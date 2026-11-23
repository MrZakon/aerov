package net.aero.visuals.render;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public final class VisualTarget {
    private Object world;
    private int id = -1;
    private UUID uuid;
    private long expires;

    public void remember(Minecraft client, LivingEntity target, long duration) {
        world = client.level;
        id = target.getId();
        uuid = target.getUUID();
        expires = System.nanoTime() + duration;
    }

    public LivingEntity lastAttacked(Minecraft client, long now) {
        if (world != client.level || client.level == null || id < 0 || now >= expires) return null;
        var entity = client.level.getEntity(id);
        return entity instanceof LivingEntity living && living.isAlive() && uuid.equals(living.getUUID()) ? living : null;
    }

    public void clear() {
        world = null;
        id = -1;
        uuid = null;
        expires = 0;
    }

    public static LivingEntity underCrosshair(Minecraft client) {
        return client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && living != client.player && living.isAlive() ? living : null;
    }
}
