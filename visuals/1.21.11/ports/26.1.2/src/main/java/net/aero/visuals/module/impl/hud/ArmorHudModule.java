package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;

public final class ArmorHudModule extends HudWidgetModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] PERCENT_LABELS = new String[101];

    static {
        for (int i = 0; i < PERCENT_LABELS.length; i++) PERCENT_LABELS[i] = i + "%";
    }

    private final BooleanSetting durability = add(new BooleanSetting("durability", "Прочность в процентах", "Показывать оставшуюся прочность под каждым предметом брони.", true));
    public ArmorHudModule() { super("armor_hud", "ArmorHUD", "Показывает надетую броню, пустые слоты и оставшуюся прочность каждого предмета.", new HudPosition(HudAnchor.BOTTOM_CENTER, 0, 66, 1)); }
    @Override protected void refresh(Minecraft client, float tickDelta) { width = 150; height = durability.get() ? 70 : 55; }
    @Override protected void renderHud(GuiGraphicsExtractor context, Minecraft client) {
        title(context, "Броня");
        boolean showDurability = durability.get();
        for (int i = 0; i < SLOTS.length; i++) {
            int x = 9 + i * 35;
            var stack = client.player.getItemBySlot(SLOTS[i]);
            UiRenderer.roundedRect(context, x - 1, 27, 26, 24, 4, 0x25FFFFFF);
            boolean occupied = !stack.isEmpty();
            if (occupied) {
                context.item(stack, x + 4, 31);
                context.itemDecorations(client.font, stack, x + 4, 31);
            }
            if (showDurability) {
                int percent = 100;
                if (occupied && stack.isDamageableItem()) {
                    percent = remainingPercent(stack.getMaxDamage(), stack.getDamageValue());
                }
                String label = occupied ? PERCENT_LABELS[percent] : "—";
                int color = occupied && percent <= 20 ? 0xFFFF657C : 0xFFB8C4CF;
                text(context, label, x + 12 - textWidth(label, 8) / 2, 56, 8, color);
            }
        }
    }

    private static int remainingPercent(int maximum, int damage) {
        if (maximum <= 0 || damage <= 0) return 100;
        if (damage >= maximum) return 0;
        // Reserve the endpoints for states with no damage or no durability left.
        long rounded = Math.round(100.0 * (maximum - damage) / maximum);
        return (int) Math.clamp(rounded, 1, 99);
    }
}
