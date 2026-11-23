package net.aero.visuals.feature.premium;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import net.aero.visuals.feature.enhanced.HitParticles;

import java.util.HashMap;
import java.util.Map;

/** Client-thread bridges. This class never sends gameplay packets. */
public final class PremiumEvents {
    private static final long ATTACK_TIMEOUT = 800_000_000L;
    private static final Map<Integer, Attack> attacks = new HashMap<>();
    private static Object world;

    private PremiumEvents() {}

    private static final class Attack {
        final LivingEntity target;
        final Vec3d point;
        final long particleGeneration;
        long expiresAt;
        float healthBeforeDamage;
        boolean confirmed;

        Attack(LivingEntity target, Vec3d point, long expiresAt, long particleGeneration) {
            this.target = target;
            this.point = point;
            this.expiresAt = expiresAt;
            this.particleGeneration = particleGeneration;
        }
    }

    public static void attack(Entity entity) {
        if (!(entity instanceof LivingEntity target)) return;
        var particles = PremiumFeatures.get(HitParticles.class);
        long particleGeneration = particles == null ? -1 : particles.captureAttack(target);
        if (particleGeneration < 0 && !PremiumFeatures.on(WorldEffects.DamageNumbers.class)
                && !PremiumFeatures.on(WorldEffects.HitMarker.class)) return;

        var client = MinecraftClient.getInstance();
        resetWorld(client.world);
        if (attacks.size() >= 16) attacks.values().removeIf(attack -> expired(attack, System.nanoTime()));
        if (attacks.size() >= 16 && !attacks.containsKey(target.getId())) return;
        Vec3d point = client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() == target
                ? hit.getPos() : target.getEntityPos().add(0, target.getHeight() * .65, 0);
        Attack previous = attacks.get(target.getId());
        if (previous != null && previous.confirmed) showDamage(previous);
        attacks.put(target.getId(), new Attack(target, point, System.nanoTime() + ATTACK_TIMEOUT, particleGeneration));
    }

    public static void damage(int targetId, int attackerId, int directId) {
        var client = MinecraftClient.getInstance();
        resetWorld(client.world);
        Attack attack = attacks.get(targetId);
        if (client.player == null || client.world == null || attack == null) return;
        if (expired(attack, System.nanoTime()) || client.world.getEntityById(targetId) != attack.target) {
            attacks.remove(targetId);
            return;
        }
        if (attackerId != client.player.getId() || directId != client.player.getId()) {
            if (attack.confirmed) {
                // Attribute only health changes already observed before this other damage event.
                showDamage(attack);
                attacks.remove(targetId);
            }
            return;
        }
        if (attack.confirmed) return;

        if (attack.particleGeneration >= 0) {
            var particles = PremiumFeatures.get(HitParticles.class);
            if (particles != null) particles.confirmedHit(attack.target, attack.particleGeneration);
        }
        var marker = PremiumFeatures.get(WorldEffects.HitMarker.class);
        if (marker != null) marker.hit(attack.point);
        if (!PremiumFeatures.on(WorldEffects.DamageNumbers.class)) {
            attacks.remove(targetId);
            return;
        }
        attack.confirmed = true;
        attack.healthBeforeDamage = health(attack.target);
        attack.expiresAt = System.nanoTime() + ATTACK_TIMEOUT;
    }

    public static void tick(MinecraftClient client) {
        resetWorld(client.world);
        if (client.player == null) {
            attacks.clear();
            return;
        }
        long now = System.nanoTime();
        attacks.values().removeIf(attack -> expired(attack, now) || attack.confirmed && showDamage(attack));
    }

    private static boolean expired(Attack attack, long now) {
        return attack.target.isRemoved() || now >= attack.expiresAt;
    }

    private static boolean showDamage(Attack attack) {
        var target = attack.target;
        float amount = attack.healthBeforeDamage - health(target);
        if (amount <= .001f) return false;
        var numbers = PremiumFeatures.get(WorldEffects.DamageNumbers.class);
        if (numbers != null) numbers.damage(target.getEntityPos().add(0, target.getHeight() * .9, 0), amount);
        return true;
    }

    private static float health(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private static void resetWorld(Object current) {
        if (world == current) return;
        world = current;
        attacks.clear();
    }

    public static void pickup(int entityId, int collector, int amount) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || collector != client.player.getId()
                || !(client.world.getEntityById(entityId) instanceof ItemEntity item)) return;
        var burst = PremiumFeatures.get(WorldEffects.PickBlowup.class);
        if (burst != null) burst.pickup(item.getEntityPos());
    }
}
