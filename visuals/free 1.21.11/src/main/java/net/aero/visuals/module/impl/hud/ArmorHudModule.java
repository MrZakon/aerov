package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;

public final class ArmorHudModule extends HudWidgetModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final BooleanSetting durability = add(new BooleanSetting("durability", "Прочность в процентах", "Показывать оставшуюся прочность под каждым предметом брони.", true));
    public ArmorHudModule() { super("armor_hud", "ArmorHUD", "Показывает надетую броню, пустые слоты и оставшуюся прочность каждого предмета.", new HudPosition(HudAnchor.BOTTOM_CENTER, 0, 66, 1)); }
    @Override protected void refresh(MinecraftClient client, float tickDelta) { width = 150; height = durability.get() ? 70 : 55; }
    @Override protected void renderHud(DrawContext context, MinecraftClient client) {
        title(context, "Броня");
        for (int i = 0; i < SLOTS.length; i++) {
            int x = 9 + i * 35;
            var stack = client.player.getEquippedStack(SLOTS[i]);
            UiRenderer.roundedRect(context, x - 1, 27, 26, 24, 4, 0x25FFFFFF);
            if (!stack.isEmpty()) {
                context.drawItem(stack, x + 4, 31);
                context.drawStackOverlay(client.textRenderer, stack, x + 4, 31);
            }
            if (durability.get()) {
                int percent = stack.isEmpty() ? 0 : stack.isDamageable() ? (int) Math.round(100.0 * Math.max(0, stack.getMaxDamage() - stack.getDamage()) / Math.max(1, stack.getMaxDamage())) : 100;
                String label = stack.isEmpty() ? "—" : percent + "%";
                text(context, label, x + 12 - textWidth(label, 8) / 2, 56, 8, percent <= 20 && !stack.isEmpty() ? 0xFFFF657C : 0xFFB8C4CF);
            }
        }
    }
}
