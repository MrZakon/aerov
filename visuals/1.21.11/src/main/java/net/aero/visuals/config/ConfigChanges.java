package net.aero.visuals.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.gui.KeyUtils;
import net.aero.visuals.localization.AeroI18n;
import net.aero.visuals.localization.LocalText;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class ConfigChanges {
    private ConfigChanges() {}

    public static List<String> describe(ClientRuntime runtime, JsonObject previous, JsonObject incoming) {
        ConfigSnapshots.validate(incoming);
        var lines = new ArrayList<String>();
        JsonObject oldVisual = object(previous, "visuals");
        JsonObject newVisual = object(incoming, "visuals");
        JsonObject oldModules = object(oldVisual, "modules");
        JsonObject newModules = object(newVisual, "modules");
        int schema = integer(newVisual.get("schemaVersion"), 0);

        for (var module : runtime.modules().all()) {
            String id = module.id();
            if (!oldModules.has(id)) continue;
            JsonObject old = object(oldModules, id);
            JsonObject next = object(newModules, id);
            String label = AeroI18n.moduleName(module);
            boolean wasEnabled = module.isConfiguredEnabled();
            boolean enabled = schema < 6 ? id.equals("wm_island") : bool(next.get("enabled"), wasEnabled);
            if (!module.isLocked() && enabled != wasEnabled) {
                lines.add((enabled ? tr("Включить", "Enable", "Увімкнути") : tr("Выключить", "Disable", "Вимкнути")) + " · " + label);
            }
            int changed = 0;
            for (var setting : module.settings()) {
                if (next.has(setting.key()) && !Objects.equals(old.get(setting.key()), next.get(setting.key()))) changed++;
            }
            if (changed > 0) lines.add(label + " · " + tr("Настройки", "Settings", "Налаштування") + " ×" + changed);
        }
        changedKeys(lines, object(oldVisual, "hud"), object(newVisual, "hud"),
                tr("Положение HUD", "HUD position", "Положення HUD"), runtime);
        JsonObject oldUi = object(previous, "interface");
        JsonObject newUi = object(incoming, "interface");
        changedKeys(lines, object(oldUi, "hudPositions"), object(newUi, "hudPositions"),
                tr("Положение виджета", "Widget position", "Положення віджета"), runtime);
        int oldMenu = integer(oldVisual.get("menuKey"), 0);
        int newMenu = integer(newVisual.get("menuKey"), oldMenu);
        addBind(lines, "ClickGUI", oldMenu, newMenu);
        JsonObject oldBinds = object(oldUi, "keybinds");
        JsonObject newBinds = object(newUi, "keybinds");
        for (var entry : oldBinds.entrySet()) {
            String key = entry.getKey();
            JsonElement next = newBinds.get(key);
            if (next != null) addBind(lines, key, integer(entry.getValue(), 0), integer(next, 0));
        }
        for (var module : runtime.modules().all()) {
            if (!oldModules.has(module.id())) continue;
            JsonObject old = object(oldModules, module.id());
            JsonObject next = object(newModules, module.id());
            int before = integer(old.get("keybind"), 0), after = integer(next.get("keybind"), 0);
            JsonElement oldUiKey = oldBinds.get(module.name()), newUiKey = newBinds.get(module.name());
            if (oldUiKey != null && newUiKey != null && integer(oldUiKey, 0) == before
                    && integer(newUiKey, 0) == after) continue;
            addBind(lines, AeroI18n.moduleName(module), before, after);
        }
        if (object(newVisual, "client").entrySet().stream().anyMatch(e -> !Objects.equals(e.getValue(), object(oldVisual, "client").get(e.getKey())))
                || List.of("hudSettings", "guiState", "favouriteModules", "collapsedModules", "savedColors").stream()
                .anyMatch(key -> !Objects.equals(oldUi.get(key), newUi.get(key)))) {
            lines.add(tr("Настройки оформления и интерфейса", "Appearance and interface settings", "Налаштування оформлення й інтерфейсу"));
        }
        if (lines.isEmpty()) lines.add(tr("Изменений нет", "No changes", "Змін немає"));
        return List.copyOf(lines);
    }

    private static void addBind(List<String> lines, String label, int before, int after) {
        if (before != after) lines.add(tr("Клавиша", "Key", "Клавіша") + " · " + label + " · "
                + bindName(before) + " → " + bindName(after));
    }

    private static String bindName(int key) {
        return key == 0 || key == -1 ? tr("нет", "none", "немає") : KeyUtils.getShortKeyName(key);
    }

    private static void changedKeys(List<String> lines, JsonObject before, JsonObject after, String label, ClientRuntime runtime) {
        var keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) if (!Objects.equals(before.get(key), after.get(key))) {
            String name = runtime.modules().find(key).map(AeroI18n::moduleName).orElse(key);
            lines.add(label + " · " + name + " · " + position(before.get(key)) + " → " + position(after.get(key)));
        }
    }

    private static String position(JsonElement value) {
        if (!(value instanceof JsonObject point)) return tr("По умолчанию", "Default", "Типово");
        if (point.has("x") && point.has("y")) {
            String coordinates = point.get("x").getAsString() + ", " + point.get("y").getAsString();
            return point.has("anchor") ? point.get("anchor").getAsString() + " " + coordinates : coordinates;
        }
        return tr("Своя позиция", "Custom position", "Власна позиція");
    }

    private static JsonObject object(JsonObject from, String key) {
        return from.get(key) instanceof JsonObject object ? object : new JsonObject();
    }

    private static int integer(JsonElement value, int fallback) {
        return value == null ? fallback : value.getAsInt();
    }

    private static boolean bool(JsonElement value, boolean fallback) {
        return value == null ? fallback : value.getAsBoolean();
    }

    private static String tr(String ru, String en, String uk) { return LocalText.pick(ru, en, uk); }
}

