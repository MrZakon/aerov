package net.aero.visuals.module.impl.hud;

import client.concurrent.SystemClient;
import client.module.AeroModuleAdapter;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.gui.ClickGuiScreen;
import net.aero.visuals.gui.KeyUtils;
import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KeybindsHudModule extends HudWidgetModule {
    private final DoubleSetting maximum = add(new DoubleSetting("max_rows", "Максимум модулей", "Максимальное число активных модулей с назначенными клавишами в списке.", 10, 1, 20, 1));
    private long nextRefresh;
    private record Binding(String name, int legacyKey, String menuKey, String label) {}
    private final Map<Module, Binding> bindings = new HashMap<>();
    private final List<Binding> active = new ArrayList<>();
    private static final Comparator<Binding> BY_NAME = Comparator.comparing(Binding::name);
    public KeybindsHudModule() { super("keybinds_hud", "Keybinds", "Показывает включённые модули с назначенными клавишами. Постоянные модули без бинда в список не попадают.", new HudPosition(HudAnchor.TOP_LEFT, 8, 60, 1)); }
    @Override protected void refresh(Minecraft client, float tickDelta) {
        long now = System.nanoTime();
        if (nextRefresh == 0 || now - nextRefresh >= 0) {
            nextRefresh = now + 100_000_000L;
            active.clear();
            var runtime = ClientRuntime.getNullable();
            if (runtime != null) {
                var system = SystemClient.INSTANCE;
                var registry = system == null ? null : system.getModuleRegistry();
                for (Module module : runtime.modules().all()) {
                    if (!module.isEnabled() || module.isLocked()) continue;
                    int legacyKey = ClickGuiScreen.getKeybind(module);
                    String menuKey = null;
                    if (registry != null && registry.getModuleByString(module.name()) instanceof AeroModuleAdapter adapter
                            && adapter.getAeroModule() == module) {
                        var setting = adapter.getKeybindSetting();
                        if (setting != null && setting.getValue() >= 0) menuKey = setting.getText();
                    }
                    if (menuKey == null && legacyKey <= 0) continue;
                    Binding binding = bindings.get(module);
                    if (binding == null || binding.legacyKey() != legacyKey
                            || !java.util.Objects.equals(binding.menuKey(), menuKey)) {
                        String label = menuKey == null ? KeyUtils.getShortKeyName(legacyKey) : menuKey;
                        binding = new Binding(module.name(), legacyKey, menuKey, "[" + label + "]");
                        bindings.put(module, binding);
                    }
                    active.add(binding);
                }
            }
            active.sort(BY_NAME);
        }
        content = !active.isEmpty();
        width = 190;
        height = 28 + Math.max(1, Math.min(active.size(), maximum.get().intValue())) * 19;
    }
    @Override
    protected void onDisable() {
        active.clear();
        bindings.clear();
        nextRefresh = 0;
    }
    @Override protected void renderHud(GuiGraphicsExtractor context, Minecraft client) {
        title(context, "Бинды");
        if (active.isEmpty()) { text(context, net.aero.visuals.localization.LocalText.get("Нет активных биндов"), 8, 30, 8, 0xFFB8C4CF); return; }
        int limit = Math.min(active.size(), maximum.get().intValue());
        for (int i = 0; i < limit; i++) {
            var binding = active.get(i);
            double keyWidth = textWidth(binding.label(), 8);
            int y = 29 + i * 19;
            text(context, fit(binding.name(), width - 26 - keyWidth, 8), 8, y, 8, 0xFFF1F4FF);
            text(context, binding.label(), width - 8 - keyWidth, y, 8, 0xFF5CE1E6);
        }
    }
}
