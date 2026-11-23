package net.aero.visuals.render;

import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

public final class VisualTarget {
    private Object world;
    private int id = -1;
    private UUID uuid;
    private long expires;

    public void remember(MinecraftClient client, LivingEntity target, long duration) {
        world = client.world;
        id = target.getId();
        uuid = target.getUuid();
        expires = System.nanoTime() + duration;
    }

    public LivingEntity lastAttacked(MinecraftClient client, long now) {
        if (world != client.world || client.world == null || id < 0 || now >= expires) return null;
        var entity = client.world.getEntityById(id);
        return entity instanceof LivingEntity living && living.isAlive() && uuid.equals(living.getUuid()) ? living : null;
    }

    public void clear() {
        world = null;
        id = -1;
        uuid = null;
        expires = 0;
    }

    public static LivingEntity underCrosshair(MinecraftClient client) {
        return client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && living != client.player && living.isAlive() ? living : null;
    }
}
