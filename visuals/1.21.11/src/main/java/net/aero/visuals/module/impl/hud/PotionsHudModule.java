package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PotionsHudModule extends HudWidgetModule {
    private static final Comparator<Row> EFFECT_ORDER = Comparator.comparing(row -> row.name);
    private final DoubleSetting maximum = add(new DoubleSetting("max_rows", "Максимум эффектов",
            "Максимальное число эффектов на панели. Полный список доступен в инвентаре Minecraft.", 8, 1, 16, 1));
    private final List<Row> rows = new ArrayList<>();
    private final Map<RegistryEntry<StatusEffect>, Row> active = new HashMap<>();
    private WeakReference<Object> effectOwner;
    private long nextRead;

    private static final class Row {
        final Identifier texture;
        final int color;
        StatusEffectInstance effect;
        String name;
        String label;
        String time;
        int amplifier;
        int maxDuration;
        long lastSecond = Long.MIN_VALUE;
        boolean infinite;

        Row(StatusEffect type) {
            var id = Registries.STATUS_EFFECT.getId(type);
            texture = id == null ? null : Identifier.of(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png");
            color = 0xFF000000 | type.getColor();
        }

        String time(int duration, boolean unlimited) {
            long second = unlimited ? -1 : Math.max(0, (duration + 19L) / 20);
            if (second != lastSecond) {
                lastSecond = second;
                time = unlimited ? "∞" : seconds(duration);
            }
            return time;
        }
    }

    public PotionsHudModule() {
        super("potion_hud", "Potions", "Показывает активные эффекты, их уровень и оставшееся время. Без эффектов панель видна только при настройке.",
                new HudPosition(HudAnchor.TOP_RIGHT, 8, 50, 1));
    }

    @Override
    protected void refresh(MinecraftClient client, float tickDelta) {
        var player = client.player;
        boolean ownerChanged = effectOwner == null || effectOwner.get() != player;
        long now = System.nanoTime();
        if (ownerChanged) {
            active.clear();
            effectOwner = new WeakReference<>(player);
        }
        if (ownerChanged || now - nextRead >= 0) {
            nextRead = now + 100_000_000L;
            rows.clear();
            for (var row : active.values()) row.effect = null;
            for (var effect : player.getStatusEffects()) {
                var row = active.computeIfAbsent(effect.getEffectType(), key -> new Row(key.value()));
                String name = effect.getEffectType().value().getName().getString();
                int amplifier = effect.getAmplifier();
                boolean infinite = effect.isInfinite();
                int duration = Math.max(1, effect.getDuration());
                if (row.label == null || amplifier != row.amplifier || !name.equals(row.name)) {
                    row.name = name;
                    row.label = amplifier <= 0 ? name : name + " " + (amplifier + 1);
                }
                if (row.maxDuration == 0 || amplifier != row.amplifier || infinite != row.infinite) {
                    row.maxDuration = duration;
                } else {
                    row.maxDuration = Math.max(row.maxDuration, duration);
                }
                row.amplifier = amplifier;
                row.infinite = infinite;
                row.effect = effect;
                rows.add(row);
            }
            active.values().removeIf(row -> row.effect == null);
            rows.sort(EFFECT_ORDER);
        }
        content = !rows.isEmpty();
        width = 210;
        height = 28 + Math.max(1, Math.min(rows.size(), maximum.get().intValue())) * 24;
    }

    @Override
    protected void onDisable() {
        rows.clear();
        active.clear();
        effectOwner = null;
    }

    @Override
    protected void renderHud(DrawContext context, MinecraftClient client) {
        title(context, "Эффекты");
        if (rows.isEmpty()) {
            text(context, LocalText.get("Нет активных эффектов"), 8, 30, 8, 0xFFB8C4CF);
            return;
        }
        int limit = Math.min(rows.size(), maximum.get().intValue());
        for (int i = 0; i < limit; i++) {
            var row = rows.get(i);
            int y = 29 + i * 24;
            if (row.texture != null) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, row.texture, 8, y - 1, 0, 0, 12, 12, 18, 18, 18, 18);
            }
            int duration = row.effect.getDuration();
            String time = row.time(duration, row.infinite);
            double timeWidth = textWidth(time, 8);
            text(context, fit(row.label, width - 41 - timeWidth, 8), 25, y, 8, 0xFFF1F4FF);
            double progress = row.infinite ? 1 : Math.clamp(duration / (double) row.maxDuration, 0, 1);
            UiRenderer.roundedRect(context, 25, y + 13, width - 33, 2, 1, 0x28FFFFFF);
            UiRenderer.roundedRect(context, 25, y + 13, (width - 33) * progress, 2, 1, row.color);
            text(context, time, width - 8 - timeWidth, y, 8, !row.infinite && duration < 200 ? 0xFFFFC56B : 0xFFB8C4CF);
        }
    }
}
