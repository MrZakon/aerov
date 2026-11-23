package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class HitSoundsModule extends Module {
    public enum Sound { ORB, AMETHYST, ARROW, CRITICAL }
    private final EnumSetting<Sound> sound = add(new EnumSetting<>("sound", "Звук", "Выберите звук удара.", Sound.ORB, Sound.class));
    private final DoubleSetting volume = add(new DoubleSetting("volume", "Громкость", "Громкость звука удара.", 0.6, 0.1, 1.0, 0.05));
    private final DoubleSetting pitch = add(new DoubleSetting("pitch", "Высота", "Высота звука удара.", 1.0, 0.5, 2.0, 0.05));

    public HitSoundsModule() {
        super("hit_sounds", "HitSounds", "Воспроизводит выбранный звук при атаке живой цели.", ModuleCategory.PVP);
    }

    public void onAttack(Entity entity) {
        if (!isEnabled() || !(entity instanceof LivingEntity) || Minecraft.getInstance().player == null) return;
        SoundEvent event = switch (sound.get()) {
            case ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case AMETHYST -> SoundEvents.AMETHYST_BLOCK_HIT;
            case ARROW -> SoundEvents.ARROW_HIT_PLAYER;
            case CRITICAL -> SoundEvents.PLAYER_ATTACK_CRIT;
        };
        Minecraft.getInstance().player.playSound(event, volume.get().floatValue(), pitch.get().floatValue());
    }
}
