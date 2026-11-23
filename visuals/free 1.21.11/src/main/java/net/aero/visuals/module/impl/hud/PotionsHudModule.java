package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PotionsHudModule extends HudWidgetModule {
    private final DoubleSetting maximum = add(new DoubleSetting("max_rows", "Максимум эффектов", "Максимальное число эффектов на панели. Полный список доступен в инвентаре Minecraft.", 8, 1, 16, 1));
    private final List<StatusEffectInstance> effects = new ArrayList<>();
    private long nextRead;
    private final java.util.Map<net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect>,Integer> durations=new java.util.HashMap<>();
    public PotionsHudModule() { super("potion_hud", "Potions", "Показывает активные эффекты, их уровень и оставшееся время. Без эффектов панель видна только при настройке.", new HudPosition(HudAnchor.TOP_RIGHT, 8, 50, 1)); }
    @Override protected void refresh(MinecraftClient client, float tickDelta) {
        if (System.nanoTime() >= nextRead) {
            nextRead = System.nanoTime() + 100_000_000L;
            effects.clear(); effects.addAll(client.player.getStatusEffects());
            durations.keySet().removeIf(key->effects.stream().noneMatch(effect->effect.getEffectType().equals(key)));for(var effect:effects)durations.merge(effect.getEffectType(),Math.max(1,effect.getDuration()),Math::max);
            effects.sort(Comparator.comparing(e -> e.getEffectType().value().getName().getString()));
        }
        content = !effects.isEmpty(); width = 210; height = 28 + Math.max(1, Math.min(effects.size(), maximum.get().intValue())) * 24;
    }
    @Override protected void onDisable() { effects.clear(); durations.clear(); nextRead = 0; }
    @Override protected void renderHud(DrawContext context, MinecraftClient client) {
        title(context, "Эффекты");
        if (effects.isEmpty()) { text(context, net.aero.visuals.localization.LocalText.get("Нет активных эффектов"), 8, 30, 8, 0xFFB8C4CF); return; }
        for (int i = 0; i < Math.min(effects.size(), maximum.get().intValue()); i++) {
            var effect = effects.get(i); double y = 29 + i * 24;
            var effectId=net.minecraft.registry.Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
            context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.util.Identifier.of(effectId.getNamespace(),"textures/mob_effect/"+effectId.getPath()+".png"),8,(int)y-1,0,0,12,12,18,18,18,18);
            String time = effect.isInfinite() ? "∞" : seconds(effect.getDuration());
            String name = effect.getEffectType().value().getName().getString() + (effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "");
            text(context, fit(name, width - 41 - textWidth(time, 8), 8), 25, y, 8, 0xFFF1F4FF);
            double progress=effect.isInfinite()?1:Math.clamp(effect.getDuration()/(double)durations.getOrDefault(effect.getEffectType(),Math.max(1,effect.getDuration())),0,1);UiRenderer.roundedRect(context,25,y+13,width-33,2,1,0x28FFFFFF);UiRenderer.roundedRect(context,25,y+13,(width-33)*progress,2,1,0xFF000000|effect.getEffectType().value().getColor());
            text(context, time, width - 8 - textWidth(time, 8), y, 8, !effect.isInfinite() && effect.getDuration() < 200 ? 0xFFFFC56B : 0xFFB8C4CF);
        }
    }
}
