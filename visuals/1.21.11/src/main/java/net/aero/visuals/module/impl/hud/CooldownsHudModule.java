package net.aero.visuals.module.impl.hud;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.mixin.ItemCooldownManagerAccessor;
import net.aero.visuals.mixin.ItemCooldownEntryAccessor;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CooldownsHudModule extends HudWidgetModule {
    private final DoubleSetting maximum = add(new DoubleSetting("max_rows", "Максимум предметов", "Максимальное число одновременно отображаемых кулдаунов.", 8, 1, 16, 1));
    private static final Comparator<Row> BY_NAME = Comparator.comparing(row -> row.name);
    private final Map<Identifier, ItemStack> known = new HashMap<>();
    private final Map<Identifier, ItemStack> inventory = new HashMap<>();
    private final Map<Identifier, Row> rowsByGroup = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private WeakReference<Object> cooldownOwner;
    private int managerTick;
    private float partialTick;

    private static final class Row {
        ItemStack stack;
        String name;
        int startTick, endTick;
        long tenths = -1;
        String time;

        float ticksLeft(int tick, float partialTick) {
            return (endTick - tick) - partialTick;
        }

        String time(float ticksLeft) {
            long value = Math.round(ticksLeft / 2.0);
            if (value != tenths) {
                tenths = value;
                time = value / 10 + "." + value % 10 + " с";
            }
            return time;
        }
    }

    public CooldownsHudModule() { super("cooldowns_hud", "Cooldowns", "Показывает действующие кулдауны предметов и секунды до их окончания. Использует реальные таймеры Minecraft, включая серверные группы кулдаунов.", new HudPosition(HudAnchor.BOTTOM_LEFT, 8, 64, 1)); }
    @Subscribe
    private void tick(TickEvent.Client event) {
        var player = event.client().player;
        if (player == null) {
            if (cooldownOwner != null) clearRows();
            return;
        }

        var manager = player.getItemCooldownManager();
        if (cooldownOwner == null || cooldownOwner.get() != manager) {
            clearRows();
            cooldownOwner = new WeakReference<>(manager);
        }
        var items = player.getInventory();
        inventory.clear();
        for (int i = 0; i < items.size(); i++) {
            var stack = items.getStack(i);
            if (!stack.isEmpty()) inventory.put(manager.getGroup(stack), stack);
        }
        var access = (ItemCooldownManagerAccessor) manager;
        var timers = access.aero$entries();
        known.keySet().removeIf(key -> !inventory.containsKey(key) && !timers.containsKey(key));
        for (var entry : inventory.entrySet()) {
            var stack = entry.getValue();
            var previous = known.get(entry.getKey());
            if (previous == null || !ItemStack.areItemsAndComponentsEqual(previous, stack)) {
                known.put(entry.getKey(), stack.copyWithCount(1));
            }
        }
        inventory.clear();

        managerTick = access.aero$tick();
        rows.clear();
        rowsByGroup.keySet().retainAll(timers.keySet());
        for (var entry : timers.entrySet()) {
            if (!(entry.getValue() instanceof ItemCooldownEntryAccessor timer)) continue;
            if (timer.aero$endTick() - managerTick <= 0) continue;
            var group = entry.getKey();
            var row = rowsByGroup.computeIfAbsent(group, ignored -> new Row());
            var stack = known.get(group);
            if (stack != null) {
                row.stack = stack;
            } else if (row.stack == null) {
                row.stack = Registries.ITEM.getOptionalValue(group).map(ItemStack::new).orElse(ItemStack.EMPTY);
            }
            row.name = row.stack.isEmpty() ? group.getPath().replace('_', ' ') : row.stack.getName().getString();
            row.startTick = timer.aero$startTick();
            row.endTick = timer.aero$endTick();
            rows.add(row);
        }
        rows.sort(BY_NAME);
    }

    @Override
    protected void refresh(MinecraftClient client, float tickDelta) {
        partialTick = tickDelta;
        var manager = client.player.getItemCooldownManager();
        if (cooldownOwner == null || cooldownOwner.get() != manager) rows.clear();
        int visible = 0;
        int limit = maximum.get().intValue();
        for (var row : rows) {
            if (row.ticksLeft(managerTick, partialTick) <= 0) continue;
            if (++visible == limit) break;
        }
        content = visible > 0;
        width = 210;
        height = 28 + Math.max(1, visible) * 23;
    }

    @Override
    protected void onDisable() {
        clearRows();
    }

    private void clearRows() {
        known.clear();
        rows.clear();
        rowsByGroup.clear();
        inventory.clear();
        cooldownOwner = null;
        content = false;
    }

    @Override
    protected void renderHud(DrawContext context, MinecraftClient client) {
        title(context, "Перезарядки");
        if (!content) {
            text(context, net.aero.visuals.localization.LocalText.get("Нет активных кулдаунов"), 8, 30, 8, 0xFFB8C4CF);
            return;
        }
        int drawn = 0;
        int limit = maximum.get().intValue();
        for (var row : rows) {
            float left = row.ticksLeft(managerTick, partialTick);
            if (left <= 0) continue;
            int y = 27 + drawn * 23;
            if (!row.stack.isEmpty()) context.drawItem(row.stack, 8, y);
            String time = row.time(left);
            double timeWidth = textWidth(time, 8);
            text(context, fit(row.name, width - 47 - timeWidth, 8), 29, y + 1, 8, 0xFFF1F4FF);
            text(context, time, width - 8 - timeWidth, y + 1, 8, 0xFFB8C4CF);
            float progress = Math.clamp(left / Math.max(1, row.endTick - row.startTick), 0, 1);
            UiRenderer.roundedRect(context, 29, y + 14, width - 37, 2, 1, 0x30FFFFFF);
            UiRenderer.roundedRect(context, 29, y + 14, (width - 37) * progress, 2, 1, 0xFF5CE1E6);
            if (++drawn == limit) break;
        }
    }
}
