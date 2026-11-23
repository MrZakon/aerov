package net.aero.visuals.localization;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.Setting;
import net.minecraft.client.resource.language.I18n;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Complete Bilingual localization engine (Russian by default & English toggle). */
public final class AeroI18n {
    public static final String PREFIX = "aerovisuals.";

    /** 1 = Russian (Default for AeroVisuals), 0 = English. */
    public static int languageMode = 1;

    private static final Map<String, String> RU_MAP = new HashMap<>();
    private static final Map<String, String> EN_MAP = new HashMap<>();
    private static final Map<String, String> UA_MAP = new HashMap<>();

    private static final Map<String, String> RU_MODULE_NAMES = new HashMap<>();
    private static final Map<String, String> RU_MODULE_DESCS = new HashMap<>();
    private static final Map<String, String> EN_MODULE_NAMES = new HashMap<>();
    private static final Map<String, String> EN_MODULE_DESCS = new HashMap<>();
    private static final Map<String, String> UA_MODULE_NAMES = new HashMap<>();
    private static final Map<String, String> UA_MODULE_DESCS = new HashMap<>();

    static {
        RU_MODULE_NAMES.put("armor_hud", "Броня на экране");
        RU_MODULE_NAMES.put("potion_hud", "Эффекты зелий");
        RU_MODULE_DESCS.put("armor_hud", "Отображение слотов брони и прочности в HUD");
        RU_MODULE_DESCS.put("potion_hud", "Информативный список активных зелий с таймерами");
        EN_MODULE_NAMES.put("armor_hud", "ArmorHUD");
        EN_MODULE_NAMES.put("potion_hud", "Potion & Cooldown HUD");
        EN_MODULE_DESCS.put("armor_hud", "Armor slots and durability status on HUD");
        EN_MODULE_DESCS.put("potion_hud", "Clean potion effect status display with countdowns");

        UA_MODULE_NAMES.put("armor_hud", "Броня на екрані");
        UA_MODULE_NAMES.put("potion_hud", "Ефекти зілля");
        UA_MODULE_DESCS.put("armor_hud", "Відображення слотів броні та міцності в HUD");
        UA_MODULE_DESCS.put("potion_hud", "Відображення активних ефектів зілля з таймерами");

        loadLanguageMap("/assets/aerovisuals/lang/ru_ru.json", RU_MAP);
        loadLanguageMap("/assets/aerovisuals/lang/en_us.json", EN_MAP);
        loadLanguageMap("/assets/aerovisuals/lang/uk_ua.json", UA_MAP);
    }

    private static void loadLanguageMap(String path, Map<String, String> target) {
        try (InputStream in = AeroI18n.class.getResourceAsStream(path)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Map<String, String> parsed = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                    if (parsed != null) {
                        target.putAll(parsed);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private AeroI18n() {}

    public static boolean isRussian() {
        return languageMode == 1;
    }

    public static boolean isUkrainian() {
        return languageMode == 2;
    }

    public static boolean isEnglish() {
        return languageMode == 0;
    }

    public static void setLanguage(int mode) {
        languageMode = (mode == 1 || mode == 2) ? mode : 0;
    }

    public static String tr(String path, Object... args) {
        return trOr(path, path, args);
    }

    public static String trOr(String path, String fallback, Object... args) {
        String fullKey = path.startsWith(PREFIX) ? path : (PREFIX + path);
        String shortKey = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : path;

        Map<String, String> activeMap = isRussian() ? RU_MAP : (isUkrainian() ? UA_MAP : EN_MAP);
        if (activeMap.containsKey(fullKey)) {
            return format(activeMap.get(fullKey), args);
        }
        if (activeMap.containsKey(shortKey)) {
            return format(activeMap.get(shortKey), args);
        }

        // Check Minecraft's built-in I18n if available
        if (hasMcTranslation(fullKey)) {
            return getMcTranslation(fullKey, args);
        }
        if (hasMcTranslation(shortKey)) {
            return getMcTranslation(shortKey, args);
        }

        // Check embedded UI fallbacks
        String embedded = getEmbeddedFallback(shortKey);
        if (embedded != null) {
            return format(embedded, args);
        }

        return fallback;
    }

    private static boolean hasMcTranslation(String key) {
        try {
            return I18n.hasTranslation(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String getMcTranslation(String key, Object... args) {
        try {
            return I18n.translate(key, args);
        } catch (Throwable ignored) {
            return key;
        }
    }

    private static String format(String pattern, Object... args) {
        if (args == null || args.length == 0 || pattern == null) return pattern;
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (Throwable ignored) {
            return pattern;
        }
    }

    public static String moduleName(Module module) {
        if (module == null) return "";
        String id = module.id();
        if (isRussian()) {
            if (RU_MAP.containsKey(PREFIX + "module." + id + ".name")) return RU_MAP.get(PREFIX + "module." + id + ".name");
            if (RU_MAP.containsKey("module." + id + ".name")) return RU_MAP.get("module." + id + ".name");
            if (RU_MODULE_NAMES.containsKey(id)) return RU_MODULE_NAMES.get(id);
        } else if (isUkrainian()) {
            if (UA_MAP.containsKey(PREFIX + "module." + id + ".name")) return UA_MAP.get(PREFIX + "module." + id + ".name");
            if (UA_MAP.containsKey("module." + id + ".name")) return UA_MAP.get("module." + id + ".name");
            if (UA_MODULE_NAMES.containsKey(id)) return UA_MODULE_NAMES.get(id);
        } else {
            if (EN_MAP.containsKey(PREFIX + "module." + id + ".name")) return EN_MAP.get(PREFIX + "module." + id + ".name");
            if (EN_MAP.containsKey("module." + id + ".name")) return EN_MAP.get("module." + id + ".name");
            if (EN_MODULE_NAMES.containsKey(id)) return EN_MODULE_NAMES.get(id);
        }
        return module.name();
    }

    public static String moduleDescription(Module module) {
        if (module == null) return "";
        String id = module.id();
        if (isRussian()) {
            if (RU_MAP.containsKey(PREFIX + "module." + id + ".description")) return RU_MAP.get(PREFIX + "module." + id + ".description");
            if (RU_MAP.containsKey("module." + id + ".description")) return RU_MAP.get("module." + id + ".description");
            if (RU_MODULE_DESCS.containsKey(id)) return RU_MODULE_DESCS.get(id);
        } else if (isUkrainian()) {
            if (UA_MAP.containsKey(PREFIX + "module." + id + ".description")) return UA_MAP.get(PREFIX + "module." + id + ".description");
            if (UA_MAP.containsKey("module." + id + ".description")) return UA_MAP.get("module." + id + ".description");
            if (UA_MODULE_DESCS.containsKey(id)) return UA_MODULE_DESCS.get(id);
        } else {
            if (EN_MAP.containsKey(PREFIX + "module." + id + ".description")) return EN_MAP.get(PREFIX + "module." + id + ".description");
            if (EN_MAP.containsKey("module." + id + ".description")) return EN_MAP.get("module." + id + ".description");
            if (EN_MODULE_DESCS.containsKey(id)) return EN_MODULE_DESCS.get(id);
        }
        return module.description();
    }

    public static String category(ModuleCategory category) { return category == null ? "" : LocalText.get(category.displayName()); }

    public static String settingName(Setting<?> setting) {
        if (setting == null) return "";
        String key = setting.key();
        String fullKey = PREFIX + "setting." + key + ".name";
        String shortKey = "setting." + key + ".name";
        Map<String, String> activeMap = isRussian() ? RU_MAP : (isUkrainian() ? UA_MAP : EN_MAP);
        if (activeMap.containsKey(fullKey)) return activeMap.get(fullKey);
        if (activeMap.containsKey(shortKey)) return activeMap.get(shortKey);

        if (isRussian()) {
            return getCommonSettingRu(key, setting.name());
        } else if (isUkrainian()) {
            return getCommonSettingUa(key, setting.name());
        }
        return setting.name();
    }

    public static String settingDescription(Setting<?> setting) {
        if (setting == null) return "";
        String key = setting.key();
        String fullKey = PREFIX + "setting." + key + ".description";
        String shortKey = "setting." + key + ".description";
        Map<String, String> activeMap = isRussian() ? RU_MAP : (isUkrainian() ? UA_MAP : EN_MAP);
        if (activeMap.containsKey(fullKey)) return activeMap.get(fullKey);
        if (activeMap.containsKey(shortKey)) return activeMap.get(shortKey);

        return setting.description();
    }

    private static String getCommonSettingUa(String key, String fallback) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "scale" -> "Масштаб";
            case "size" -> "Розмір";
            case "speed" -> "Швидкість";
            case "color" -> "Колір";
            case "opacity", "alpha" -> "Прозорість";
            case "blur" -> "Розмиття";
            case "rainbow" -> "Веселка";
            case "rainbow_speed" -> "Швидкість веселки";
            case "glow" -> "Світіння";
            case "particles" -> "Частинки";
            case "radius" -> "Радіус";
            case "range" -> "Дистанція";
            case "thickness" -> "Товщина";
            case "volume" -> "Гучність";
            case "intensity" -> "Інтенсивність";
            case "fps" -> "Показувати FPS";
            case "ping" -> "Показувати пінг";
            case "time" -> "Показувати час";
            case "background" -> "Фон";
            case "shadow" -> "Тінь";
            case "border" -> "Обведення";
            case "rounded" -> "Скруглення";
            case "font" -> "Шрифт";
            case "smooth" -> "Плавність";
            case "fov" -> "Кут огляду";
            case "shake" -> "Трясіння екрана";
            case "mode" -> "Режим";
            case "density" -> "Щільність";
            case "enabled" -> "Увімкнено";
            case "disabled" -> "Вимкнено";
            default -> fallback;
        };
    }

    private static String getCommonSettingRu(String key, String fallback) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "scale" -> "Масштаб";
            case "size" -> "Размер";
            case "speed" -> "Скорость";
            case "color" -> "Цвет";
            case "opacity", "alpha" -> "Прозрачность";
            case "blur" -> "Размытие";
            case "rainbow" -> "Радуга";
            case "rainbow_speed" -> "Скорость радуги";
            case "glow" -> "Свечение";
            case "particles" -> "Частицы";
            case "radius" -> "Радиус";
            case "range" -> "Дистанция";
            case "thickness" -> "Толщина";
            case "volume" -> "Громкость";
            case "intensity" -> "Интенсивность";
            case "fps" -> "Показывать FPS";
            case "ping" -> "Показывать пинг";
            case "time" -> "Показывать время";
            case "background" -> "Фон";
            case "shadow" -> "Тень";
            case "border" -> "Обводка";
            case "rounded" -> "Скругление";
            case "font" -> "Шрифт";
            case "smooth" -> "Плавность";
            case "fov" -> "Угол обзора";
            case "shake" -> "Тряска экрана";
            case "mode" -> "Режим";
            case "density" -> "Плотность";
            case "enabled" -> "Включено";
            case "disabled" -> "Выключено";
            default -> fallback;
        };
    }

    private static String getEmbeddedFallback(String key) {
        boolean ru = isRussian();
        boolean ua = isUkrainian();
        return switch (key) {
            case "clickgui.user_default" -> ru ? "Пользователь AeroVisuals" : (ua ? "Користувач AeroVisuals" : "AeroVisuals User");
            case "clickgui.premium" -> ru ? "Премиум" : (ua ? "Преміум" : "Premium");
            case "clickgui.client_settings" -> ru ? "Настройки клиента" : (ua ? "Налаштування клієнта" : "Client Settings");
            case "clickgui.hud_editor" -> ru ? "Редактор HUD" : (ua ? "Редактор HUD" : "HUD Editor");
            case "clickgui.search" -> ru ? "Поиск..." : (ua ? "Пошук..." : "Search...");
            case "clickgui.interface" -> ru ? "Интерфейс" : (ua ? "Інтерфейс" : "Interface");
            case "clickgui.interface_desc" -> ru ? "Масштабирование GUI, темы и цвета" : (ua ? "Масштабування GUI, теми та кольори" : "GUI Scaling, neon themes & accent colors");
            case "clickgui.ui_scale" -> ru ? "Масштаб интерфейса" : (ua ? "Масштаб інтерфейсу" : "UI Scale");
            case "clickgui.accent_color" -> ru ? "Основной акцент" : (ua ? "Основний акцент" : "Accent Color");
            case "clickgui.secondary_cyan" -> ru ? "Вторичный бирюзовый" : (ua ? "Вторинний бірюзовий" : "Secondary Cyan");
            case "clickgui.controls" -> ru ? "Управление" : (ua ? "Керування" : "Controls");
            case "clickgui.menu_keybind" -> ru ? "Клавиша открытия ClickGUI" : (ua ? "Клавіша відкриття ClickGUI" : "ClickGUI open keybind");
            case "clickgui.gui_key" -> ru ? "Клавиша меню" : (ua ? "Клавіша меню" : "GUI Key");
            case "clickgui.audio" -> ru ? "Звук" : (ua ? "Звук" : "Audio");
            case "clickgui.audio_desc" -> ru ? "Тактильные звуковые эффекты меню" : (ua ? "Тактильні звукові ефекти меню" : "Tactile UI sound effects");
            case "clickgui.ui_sounds" -> ru ? "Звуки интерфейса" : (ua ? "Звуки інтерфейсу" : "UI Sounds");
            case "clickgui.volume" -> ru ? "Громкость" : (ua ? "Гучність" : "Volume");
            case "clickgui.color_palette" -> ru ? "Палитра цветов" : (ua ? "Палітра кольорів" : "Color Palette");
            case "clickgui.neon_accent" -> ru ? "Неоновый акцент" : (ua ? "Неоновий акцент" : "Neon Accent");
            case "clickgui.setting_color" -> ru ? "Цвет настройки" : (ua ? "Колір налаштування" : "Setting Color");
            case "clickgui.hud_editor_hint" -> ru ? "Редактор HUD — Перетащите для перемещения" : (ua ? "Редактор HUD — Перетягніть для переміщення" : "HUD Editor - Drag to move");
            case "clickgui.done_editing" -> ru ? "Готово" : (ua ? "Готово" : "Done Editing");
            case "clickgui.language" -> ru ? "Язык" : (ua ? "Мова" : "Language");
            case "clickgui.language_desc" -> ru ? "Язык интерфейса клиента" : (ua ? "Мова інтерфейсу клієнта" : "Client interface language");
            default -> null;
        };
    }

    public static String value(Object value) {
        if (value == null) return "";
        String raw = value instanceof Enum<?> enumeration ? enumeration.name() : String.valueOf(value);
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? raw : trOr("value." + normalized, raw.replace('_', ' '));
    }
}
