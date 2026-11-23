package net.aero.visuals;

import client.concurrent.SystemClient;
import client.module.Module;
import client.setting.*;

public final class LegacyModules {
    private LegacyModules() {}
    public static <T extends Module> T get(Class<T> type) {
        var client = SystemClient.INSTANCE;
        return client == null || client.getModuleRegistry() == null ? null
                : type.cast(client.getModuleRegistry().getModuleByClass(type));
    }
    public static <T extends Module> T enabled(Class<T> type) {
        T module = get(type);
        return module != null && module.isEnabled() ? module : null;
    }
    private static Setting find(Module module, String name) {
        if (module == null) return null;
        Setting direct = module.getSettingByName(name);
        return direct == null ? find(module.getSettings(), name) : direct;
    }
    private static Setting find(java.util.List<Setting> settings, String name) {
        for (Setting setting : settings) {
            if (setting.getName().equals(name)) return setting;
            if (setting instanceof CompactGroupSetting group) { Setting nested = find(group.getList(), name); if (nested != null) return nested; }
        }
        return null;
    }
    public static double number(Module module, String name, double fallback) {
        var setting = find(module, name);
        return setting instanceof SliderSetting slider ? slider.getValue() : fallback;
    }
    public static boolean flag(Module module, String name, boolean fallback) {
        var setting = find(module, name);
        return setting instanceof BooleanSetting toggle ? toggle.isFlag3() : fallback;
    }
    public static String choice(Module module, String name, String fallback) {
        var setting = find(module, name);
        String selected = setting instanceof ListSetting list ? list.getString2() : null;
        return selected == null ? fallback : selected;
    }
    public static int color(Module module, String name, int fallback) {
        var setting = find(module, name);
        return setting instanceof ColorSetting color ? color.getInt() : fallback;
    }
}
