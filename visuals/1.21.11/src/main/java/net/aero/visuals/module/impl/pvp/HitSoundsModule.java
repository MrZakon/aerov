package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.MinecraftClient;

public final class HitSoundsModule extends Module {
    public enum Sound { ORB, AMETHYST, ARROW, CRITICAL }
    private final EnumSetting<Sound> sound = add(new EnumSetting<>("sound", "Звук", "Выберите звук удара.", Sound.ORB, Sound.class));
    private final DoubleSetting volume = add(new DoubleSetting("volume", "Громкость", "Громкость звука удара.", 0.6, 0.1, 1.0, 0.05));
    private final DoubleSetting pitch = add(new DoubleSetting("pitch", "Высота", "Высота звука удара.", 1.0, 0.5, 2.0, 0.05));

    public HitSoundsModule() {
        super("hit_sounds", "HitSounds", "Воспроизводит выбранный звук при атаке живой цели.", ModuleCategory.PVP);
    }

    public void onAttack(Entity entity) {
        if (!isEnabled() || !(entity instanceof LivingEntity) || MinecraftClient.getInstance().player == null) return;
        SoundEvent event = switch (sound.get()) {
            case ORB -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case AMETHYST -> SoundEvents.BLOCK_AMETHYST_BLOCK_HIT;
            case ARROW -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
            case CRITICAL -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
        };
        MinecraftClient.getInstance().player.playSound(event, volume.get().floatValue(), pitch.get().floatValue());
    }
}
