package net.aero.visuals.module.impl.hud;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.gui.ClickGuiScreen;
import net.aero.visuals.gui.KeyUtils;
import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.setting.DoubleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

public final class KeybindsHudModule extends HudWidgetModule {
    private final DoubleSetting maximum = add(new DoubleSetting("max_rows", "Максимум модулей", "Максимальное число активных модулей с назначенными клавишами в списке.", 10, 1, 20, 1));
    private long nextRefresh;
    private final List<Module> active = new ArrayList<>();
    public KeybindsHudModule() { super("keybinds_hud", "Keybinds", "Показывает включённые модули с назначенными клавишами. Постоянные модули без бинда в список не попадают.", new HudPosition(HudAnchor.TOP_LEFT, 8, 60, 1)); }
    @Override protected void refresh(MinecraftClient client, float tickDelta) {
        long now=System.nanoTime();if(now<nextRefresh)return;nextRefresh=now+100_000_000L;
        active.clear();
        var runtime = ClientRuntime.getNullable();
        if (runtime != null) for (var module : runtime.modules().all())
            if (module.isEnabled() && !module.isLocked() && ClickGuiScreen.getKeybind(module) > 0) active.add(module);
        active.sort(Comparator.comparing(Module::name));
        content = !active.isEmpty(); width = 190; height = 28 + Math.max(1, Math.min(active.size(), maximum.get().intValue())) * 19;
    }
    @Override protected void onDisable() { active.clear();nextRefresh=0; }
    @Override protected void renderHud(DrawContext context, MinecraftClient client) {
        title(context, "Бинды");
        if (active.isEmpty()) { text(context, net.aero.visuals.localization.LocalText.get("Нет активных биндов"), 8, 30, 8, 0xFFB8C4CF); return; }
        for (int i = 0; i < Math.min(active.size(), maximum.get().intValue()); i++) {
            var module = active.get(i); String key = "[" + KeyUtils.getShortKeyName(ClickGuiScreen.getKeybind(module)) + "]";
            int y = 29 + i * 19;
            text(context, fit(module.name(), width - 26 - textWidth(key, 8), 8), 8, y, 8, 0xFFF1F4FF);
            text(context, key, width - 8 - textWidth(key, 8), y, 8, 0xFF5CE1E6);
        }
    }
}
